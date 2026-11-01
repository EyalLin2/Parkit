import MapLibre
import SwiftUI

/// ParkIt's own vector style (MapStyle.kt): one template (map_style.json, shared with
/// Android) and two palettes. MapLibre iOS loads styles by URL, so the filled-in JSON
/// goes to a cache file.
enum MapStyle {
    private static let light: [String: String] = [
        "land": "#F3F2EF", "park": "#CFEAC6", "sand": "#F5EED9", "water": "#A6D2F2", "hospital": "#F8E3E3", "school": "#F3EEDF",
        "commercial": "#EFEBF3", "building": "#E6E3DE", "buildingOutline": "#D9D5CF", "path": "#C9C6C0", "road": "#FFFFFF",
        "roadCasing": "#DCDAD5", "arterial": "#FFF1B8", "arterialCasing": "#E8D27C", "motorway": "#F9CF6E", "motorwayCasing": "#DDA73F",
        "rail": "#C4C1BB", "boundary": "#A9A49C", "label": "#3C4043", "labelFaint": "#80868B", "roadLabel": "#5F6368",
        "waterLabel": "#4A7FB0", "halo": "#FFFFFF",
    ]
    private static let dark: [String: String] = [
        "land": "#1D222B", "park": "#1E3127", "sand": "#2A2A24", "water": "#0F2236", "hospital": "#2E2228", "school": "#2A2A26",
        "commercial": "#242530", "building": "#262C36", "buildingOutline": "#2E3540", "path": "#3A414D", "road": "#363D4A",
        "roadCasing": "#262B34", "arterial": "#4A4F5C", "arterialCasing": "#2A2F38", "motorway": "#7A6A3E", "motorwayCasing": "#3A3426",
        "rail": "#3A414D", "boundary": "#5A6270", "label": "#D5D9E0", "labelFaint": "#8A93A2", "roadLabel": "#A9B1BE",
        "waterLabel": "#6C93B8", "halo": "#1D222B",
    ]

    static func url(dark isDark: Bool) -> URL {
        let name = "map_style_\(isDark ? "dark" : "light")_\(L10n.language).json"
        let out = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        guard let src = Bundle.main.url(forResource: "map_style", withExtension: "json"), var style = try? String(contentsOf: src, encoding: .utf8) else { return out }
        for (token, color) in isDark ? dark : light { style = style.replacingOccurrences(of: "{{\(token)}}", with: color) }
        let names = L10n.isHebrew ? #"["coalesce", ["get", "name:he"], ["get", "name"]]"# : #"["coalesce", ["get", "name:en"], ["get", "name_en"], ["get", "name"]]"#
        style = style.replacingOccurrences(of: "{{name}}", with: names)
        try? style.write(to: out, atomically: true, encoding: .utf8)
        return out
    }
}

struct ParkMapView: UIViewRepresentable {
    static let sourceSpots = "spots", sourceLots = "parking-lots", sourceCar = "parked-car"
    static let layerClusters = "spots-clusters", layerCount = "spots-cluster-count", layerPins = "spots-pins", layerLots = "parking-lots-pins", layerCar = "parked-car-pin"
    static let lotIcon = "lot-pin", carIcon = "car-pin"

    @ObservedObject var model: MapModel
    let dark: Bool
    let bottomInset: CGFloat

    func makeCoordinator() -> Coordinator { Coordinator(model: model, dark: dark) }

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero, styleURL: MapStyle.url(dark: dark))
        map.delegate = context.coordinator
        map.automaticallyAdjustsContentInset = false
        map.logoView.isHidden = true
        map.isPitchEnabled = false
        map.isRotateEnabled = true
        map.semanticContentAttribute = .forceLeftToRight
        map.compassViewMargins = CGPoint(x: 16, y: 170)
        map.attributionButtonPosition = .bottomLeft
        map.attributionButtonMargins = CGPoint(x: 12, y: bottomInset)
        let camera = model.initialCamera
        map.setCenter(camera.center, zoomLevel: camera.zoom, animated: false)
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tapped(_:)))
        // Pins win over the map's own double-tap-to-zoom only for single taps.
        for g in map.gestureRecognizers ?? [] where (g as? UITapGestureRecognizer)?.numberOfTapsRequired == 2 {
            tap.require(toFail: g)
        }
        map.addGestureRecognizer(tap)
        model.mapView = map
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        if context.coordinator.dark != dark {
            context.coordinator.dark = dark
            model.style = nil
            map.styleURL = MapStyle.url(dark: dark)
        }
    }

    @MainActor
    final class Coordinator: NSObject, MLNMapViewDelegate {
        let model: MapModel
        var dark: Bool

        init(model: MapModel, dark: Bool) {
            self.model = model
            self.dark = dark
        }

        nonisolated func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            MainActor.assumeIsolated {
                install(style)
                model.style = style
                if model.hasPermission { mapView.showsUserLocation = true }
            }
        }

        nonisolated func mapViewDidFinishRenderingMap(_ mapView: MLNMapView, fullyRendered: Bool) {
            MainActor.assumeIsolated { if fullyRendered { model.mapRendered = true } }
        }

        nonisolated func mapView(_ mapView: MLNMapView, regionWillChangeWith reason: MLNCameraChangeReason, animated: Bool) {
            let byGesture = !reason.intersection([.gesturePan, .gesturePinch, .gestureRotate, .gestureZoomIn, .gestureZoomOut, .gestureOneFingerZoom, .gestureTilt]).isEmpty
            MainActor.assumeIsolated { model.cameraWillMove(byGesture: byGesture) }
        }

        nonisolated func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
            let center = mapView.centerCoordinate
            MainActor.assumeIsolated { model.cameraIdle(center: center) }
        }

        /// The location dot itself, in ParkIt blue.
        nonisolated func mapView(_ mapView: MLNMapView, imageFor annotation: MLNAnnotation) -> MLNAnnotationImage? { nil }

        @objc func tapped(_ g: UITapGestureRecognizer) {
            guard let map = g.view as? MLNMapView else { return }
            let point = g.location(in: map)
            let box = CGRect(x: point.x - 16, y: point.y - 24, width: 32, height: 40)
            let hits = map.visibleFeatures(in: box, styleLayerIdentifiers: [ParkMapView.layerPins, ParkMapView.layerClusters])
            if let hit = hits.first {
                if let cluster = hit as? MLNPointFeatureCluster {
                    let source = map.style?.source(withIdentifier: ParkMapView.sourceSpots) as? MLNShapeSource
                    let zoom = source?.zoomLevel(forExpanding: cluster) ?? (map.zoomLevel + 2)
                    map.setCenter(cluster.coordinate, zoomLevel: max(zoom, map.zoomLevel + 1), animated: true)
                } else if let id = hit.attribute(forKey: "id") as? String {
                    model.selectSpot(id: id)
                }
                return
            }
            // Free spots win a tap; only then try the lots underneath.
            if let lot = map.visibleFeatures(in: box, styleLayerIdentifiers: [ParkMapView.layerLots]).first,
               let id = (lot.attribute(forKey: "id") as? NSNumber)?.intValue {
                model.selectLot(id: id)
            }
        }

        /// The map engine itself groups, positions and hit-tests pins.
        private func install(_ style: MLNStyle) {
            func constant(_ v: Any) -> NSExpression { NSExpression(forConstantValue: v) }
            let empty = MLNShapeCollectionFeature(shapes: [])

            // Car parks sit under the spot pins, and only from street zoom.
            style.setImage(Pins.lotPin(), forName: ParkMapView.lotIcon)
            let lots = MLNShapeSource(identifier: ParkMapView.sourceLots, shape: empty, options: nil)
            style.addSource(lots)
            let lotLayer = MLNSymbolStyleLayer(identifier: ParkMapView.layerLots, source: lots)
            lotLayer.iconImageName = constant(ParkMapView.lotIcon)
            lotLayer.iconAllowsOverlap = constant(true)
            lotLayer.iconIgnoresPlacement = constant(true)
            lotLayer.minimumZoomLevel = 14
            style.addLayer(lotLayer)

            let spots = MLNShapeSource(identifier: ParkMapView.sourceSpots, shape: empty, options: [
                .clustered: true, .clusterRadius: 48, .maximumZoomLevelForClustering: 14,
            ])
            style.addSource(spots)
            let clusters = MLNCircleStyleLayer(identifier: ParkMapView.layerClusters, source: spots)
            clusters.predicate = NSPredicate(format: "cluster == YES")
            clusters.circleColor = constant(UIColor(Color.pkGreen))
            clusters.circleRadius = NSExpression(format: "mgl_step:from:stops:(point_count, 18, %@)", [10: 22, 30: 27])
            clusters.circleStrokeColor = constant(dark ? UIColor(Color(hex: 0x1D222B)) : UIColor.white)
            clusters.circleStrokeWidth = constant(3)
            style.addLayer(clusters)
            let count = MLNSymbolStyleLayer(identifier: ParkMapView.layerCount, source: spots)
            count.predicate = NSPredicate(format: "cluster == YES")
            count.text = NSExpression(format: "CAST(point_count, 'NSString')")
            count.textFontNames = constant(["Noto Sans Bold"])
            count.textFontSize = constant(14)
            count.textColor = constant(UIColor.white)
            count.textAllowsOverlap = constant(true)
            count.textIgnoresPlacement = constant(true)
            style.addLayer(count)
            let pins = MLNSymbolStyleLayer(identifier: ParkMapView.layerPins, source: spots)
            pins.predicate = NSPredicate(format: "cluster != YES")
            pins.iconImageName = NSExpression(forKeyPath: "icon")
            pins.iconAnchor = constant("bottom")
            pins.iconOffset = constant(NSValue(cgVector: CGVector(dx: 0, dy: Pins.tipPadding)))
            pins.iconAllowsOverlap = constant(true)
            pins.iconIgnoresPlacement = constant(true)
            pins.symbolSortKey = NSExpression(forKeyPath: "age")
            style.addLayer(pins)

            // Your parked car, on top of everything.
            style.setImage(Pins.lotPin(), forName: ParkMapView.carIcon)
            let car = MLNShapeSource(identifier: ParkMapView.sourceCar, shape: empty, options: nil)
            style.addSource(car)
            let carLayer = MLNSymbolStyleLayer(identifier: ParkMapView.layerCar, source: car)
            carLayer.iconImageName = constant(ParkMapView.carIcon)
            carLayer.iconAnchor = constant("bottom")
            carLayer.iconAllowsOverlap = constant(true)
            carLayer.iconIgnoresPlacement = constant(true)
            style.addLayer(carLayer)
        }
    }
}
