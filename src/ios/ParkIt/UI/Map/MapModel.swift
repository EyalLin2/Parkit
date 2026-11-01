import CoreLocation
import MapLibre
import SwiftUI

typealias Coord = CLLocationCoordinate2D

extension CLLocationCoordinate2D: @retroactive Equatable {
    public static func == (a: Self, b: Self) -> Bool { a.latitude == b.latitude && a.longitude == b.longitude }

    func distance(to o: Coord) -> Double {
        CLLocation(latitude: latitude, longitude: longitude).distance(from: CLLocation(latitude: o.latitude, longitude: o.longitude))
    }
}

enum MapConst {
    static let telAviv = Coord(latitude: 32.0809, longitude: 34.7806)
    static let israelOverview = Coord(latitude: 31.6, longitude: 34.95)
    static let overviewZoom = 6.6
    static let streetZoom = 15.0
    static let radiusM = 800
    static let lotsRadiusM = 1500
    /// Same limit as the server (routers/spots.py): you report spots you can see.
    static let maxReportM = 150.0
    static let maxAccuracyAllowanceM = 100.0
    static let serviceRadiusM = 350_000.0
    static let nearCarM = 150.0
    static let nearMeM = 20.0
    static let arrivedM = 80.0
    static let arrivalPrompt: TimeInterval = 20 * 60
    static let claimHold: TimeInterval = 300 // the backend's CLAIM_TTL_SECONDS
    static let spotsPoll: Duration = .seconds(30)
    static let spotsRetry: Duration = .seconds(10)
    static let reminderSoonMin = 15
    static func inService(_ c: Coord) -> Bool { c.distance(to: telAviv) <= serviceRadiusM }
    /// The launch city: the same box as the server (service_area.py). Applies only while
    /// the server's GET /spots/service-area says tel_aviv_only.
    static func inTelAviv(_ c: Coord) -> Bool { (32.03...32.15).contains(c.latitude) && (34.73...34.85).contains(c.longitude) }
}

enum MapSheet: Identifiable, Equatable {
    case spot(SpotOut), lot(ParkingLotOut), report, sign, car, explainer, parkedPay, payChooser, stopPay(ParkingPay), levelUp(ParkItLevel)

    var id: String {
        switch self {
        case .spot(let s): "spot-\(s.id)"
        case .lot(let l): "lot-\(l.id)"
        case .report: "report"
        case .sign: "sign"
        case .car: "car"
        case .explainer: "explainer"
        case .parkedPay: "parkedPay"
        case .payChooser: "payChooser"
        case .stopPay: "stopPay"
        case .levelUp: "levelUp"
        }
    }
}

struct Snackbar: Equatable {
    let id = UUID()
    let message: String
    var actionLabel: String?
    var action: (() -> Void)?
    static func == (a: Snackbar, b: Snackbar) -> Bool { a.id == b.id }
}

enum MainAction: Equatable { case report, leave, reminder(Int) }

/// The state and behaviour of the map screen (MapScreen.kt), kept apart from its layout.
@MainActor
final class MapModel: NSObject, ObservableObject {
    private let session = Session.shared
    private let api = API.shared
    private let manager = CLLocationManager()

    weak var mapView: MLNMapView?
    var style: MLNStyle? { didSet { pushAll() } }

    @Published var spots: [SpotOut] = [] { didSet { pushSpots(); resolveAddresses() } }
    @Published var spotsLoaded = false
    @Published var spotsFailed = false
    @Published var sheet: MapSheet? { didSet { pushSpots() } }
    @Published var activeClaim: SpotOut? {
        didSet {
            session.activeClaim = activeClaim
            pushSpots()
        }
    }
    @Published var claimEndsAt: Date?
    @Published var myProfile: ProfileOut?
    @Published var typeFilter: String? { didSet { pushSpots() } }
    @Published var showLots = false {
        didSet {
            pushLots()
            if showLots { loadLots() }
        }
    }
    @Published var lots: [ParkingLotOut] = [] { didSet { pushLots() } }
    @Published var cameraMoving = false
    @Published var mapRendered = false
    @Published var deviceLocation: Coord?
    @Published var locating = true
    @Published var geocodeTarget: Coord
    @Published var resolvedAddress: String?
    @Published var resolvedStreet: String?
    @Published var addressLoading = true
    @Published var addressFailed = false
    @Published var spotAddresses: [String: String] = [:]
    /// Lookups that finished with no address: the row says so instead of "finding…".
    @Published var spotAddressFailed: Set<String> = []
    @Published var carAddressFailed = false
    /// A failed fetch means no restriction: the server still enforces it.
    @Published var telAvivOnly = false
    @Published var parkedCar: ParkedCar? {
        didSet {
            session.parkedCar = parkedCar
            ParkedNotification.sync(parkedCar)
            pushCar()
        }
    }
    @Published var showLocationWhy = false
    @Published var askArrived = false
    @Published var forecast: AreaForecast?
    @Published var forecastNoData = false
    @Published var dailyTip: BestHour?
    @Published var snackbar: Snackbar?
    @Published var clock = Date()

    private var userTookOver = false
    private var lastAccuracy: Double?
    private var pendingFly = false
    private var flyFromButton = false
    private var firstFixAt: Date?
    private var firstFix: Coord?
    private var startFix: Coord?
    /// The spot just handed on with "I'm leaving": parking there again takes it back.
    private var leftSpot: (id: String, car: ParkedCar)?
    private var targetTasks: [Task<Void, Never>] = []
    private var addressTask: Task<Void, Never>?
    private var snackTask: Task<Void, Never>?
    private var locateTimeout: Task<Void, Never>?
    private var started = false
    private var handingOn = false
    private var cell: String?
    private var pollTask: Task<Void, Never>?
    private var mapOnScreen = false
    private var inForeground = true

    override init() {
        let s = Session.shared
        let home = s.driver.homeLat.flatMap { lat in s.driver.homeLng.map { Coord(latitude: lat, longitude: $0) } }
        // Open where the phone is, or was last time: never on a made-up spot.
        let cached = CLLocationManager().location.flatMap { l -> Coord? in
            Date().timeIntervalSince(l.timestamp) < 1800 && MapConst.inService(l.coordinate) ? l.coordinate : nil
        }
        let last = s.lastFix.map { Coord(latitude: $0.lat, longitude: $0.lng) }.flatMap { MapConst.inService($0) ? $0 : nil }
        let start = cached ?? last
        startFix = start
        geocodeTarget = start ?? home ?? MapConst.telAviv
        deviceLocation = start
        parkedCar = s.parkedCar
        if let end = s.claimEndsAt, end > Date() {
            activeClaim = s.activeClaim
            claimEndsAt = end
        } else {
            s.activeClaim = nil
        }
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5
    }

    // MARK: Derived

    var initialCamera: (center: Coord, zoom: Double) {
        startFix.map { ($0, MapConst.streetZoom) } ?? (MapConst.israelOverview, MapConst.overviewZoom)
    }

    var prefs: Preferences { session.driver.preferences }

    var visibleSpots: [SpotOut] {
        var list = typeFilter.map { f in spots.filter { $0.spotType == f } } ?? spots
        if let claim = activeClaim, !list.contains(where: { $0.id == claim.id }) { list.append(claim) }
        return list
    }

    /// "Nearby" means near you when known, otherwise near the pin.
    var reference: Coord { deviceLocation ?? geocodeTarget }

    /// Spots this driver can't use go last; otherwise purely by distance.
    var sortedSpots: [SpotOut] {
        let ref = reference
        return visibleSpots.sorted {
            let a = DriverFit(spot: $0, prefs: prefs).unusable, b = DriverFit(spot: $1, prefs: prefs).unusable
            if a != b { return !a }
            return coord($0).distance(to: ref) < coord($1).distance(to: ref)
        }
    }

    /// Nobody asked the server, so this is "we don't know where you are", not "no spots".
    var locationUnknown: Bool { deviceLocation == nil && !locating }

    var outsideTelAviv: Bool { telAvivOnly && deviceLocation.map { !MapConst.inTelAviv($0) } == true }

    /// The pin is further from the phone than the server shows spots.
    var tooFar: Bool {
        guard let me = deviceLocation else { return false }
        return me.distance(to: geocodeTarget) > Double(MapConst.radiusM)
    }

    func coord(_ s: SpotOut) -> Coord { Coord(latitude: s.lat, longitude: s.lng) }
    func isRightHere(_ s: SpotOut) -> Bool { coord(s).distance(to: reference) < MapConst.nearMeM }
    func distanceLabel(_ s: SpotOut) -> String { formatDistance(coord(s).distance(to: reference)) }

    var nearMyCar: Bool {
        guard let car = parkedCar, let here = deviceLocation else { return false }
        return here.distance(to: Coord(latitude: car.lat, longitude: car.lng)) <= MapConst.nearCarM
    }

    var mainAction: MainAction {
        // A reminder only takes over the main action while a car is saved.
        if parkedCar != nil, let at = ParkingReminder.activeChangeAt {
            let left = Int(at.timeIntervalSince(clock) / 60)
            if (0...MapConst.reminderSoonMin).contains(left) { return .reminder(left) }
        }
        return nearMyCar ? .leave : .report
    }

    var hasPermission: Bool {
        manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways
    }

    // MARK: Lifecycle

    func start() {
        guard !started else { return }
        started = true
        Task { await refreshMyProfile() }
        Task { await restoreDriver() }
        Task { telAvivOnly = (try? await api.serviceArea().telAvivOnly) ?? false }
        targetChanged(debounced: false)
        resolveAddresses()
        resolveCarAddress()
        ParkedNotification.sync(parkedCar)
        NotificationCenter.default.addObserver(forName: .parkedCarChanged, object: nil, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.syncCarFromSession() }
        }
        if hasPermission {
            locate()
        } else {
            showLocationWhy = true
        }
        Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(5))
                clock = Date()
            }
        }
    }

    /// The car step also brought a returning driver's car and city onto this phone. Without
    /// it the map still needs them. onboardingSeen is cleared on logout, so the next sign-in
    /// fetches again; offline, the next launch does.
    private func restoreDriver() async {
        guard !Features.firstRunCarStep, !session.onboardingSeen, let server = try? await api.myProfile().preferences else { return }
        session.syncDriver(server)
        if let name = server.homeCity, let city = (try? await api.cities())?.first(where: { $0.name == name }) {
            session.saveDriver(DriverPrefs(preferences: server, homeLat: city.lat, homeLng: city.lng))
        }
        session.onboardingSeen = true
    }

    /// Spots come and go without this driver doing anything, so the list is polled while
    /// the map is on screen, and never in the background.
    func mapAppeared() {
        mapOnScreen = true
        updatePolling()
    }

    func mapDisappeared() {
        mapOnScreen = false
        updatePolling()
    }

    private func updatePolling() {
        pollTask?.cancel()
        pollTask = nil
        guard mapOnScreen, inForeground else { return }
        pollTask = Task { [weak self] in
            var wait = MapConst.spotsPoll
            while !Task.isCancelled {
                try? await Task.sleep(for: wait)
                guard !Task.isCancelled, let self else { return }
                wait = await self.pollSpots() ? MapConst.spotsPoll : MapConst.spotsRetry
            }
        }
    }

    /// One look at the server; false means try again soon. The sheet shows the problem and a retry button.
    private func pollSpots() async -> Bool {
        guard let me = deviceLocation else { return true }
        let target = geocodeTarget
        do {
            let result = try await api.nearbySpots(lat: target.latitude, lng: target.longitude, radiusM: MapConst.radiusM, me: me)
            guard !Task.isCancelled, target == geocodeTarget else { return true }
            if result != spots { spots = result }
            spotsLoaded = true
            spotsFailed = false
            return true
        } catch {
            if Task.isCancelled || session.token == nil { return true }
            spotsFailed = true
            return false
        }
    }

    /// "I'm leaving" on the lock screen ends the parking while the map isn't looking.
    private func syncCarFromSession() {
        if parkedCar != session.parkedCar {
            parkedCar = session.parkedCar
            refreshSpotsNow()
        }
    }

    func becameActive() {
        inForeground = true
        guard started else { return }
        syncCarFromSession()
        refreshSpotsNow()
        updatePolling()
        Task { await refreshMyProfile() }
        if hasPermission {
            manager.startUpdatingLocation()
            // Back from Settings: try again rather than staying on the fallback.
            if deviceLocation == nil, !locating { locate() }
        }
    }

    func wentToBackground() {
        inForeground = false
        updatePolling()
        manager.stopUpdatingLocation()
        // Fresh on the lock screen right when the phone is put away.
        if let car = parkedCar { ParkedNotification.show(car, delay: 1) }
    }

    func refreshMyProfile() async {
        guard let p = try? await api.myProfile() else { return }
        myProfile = p
        session.syncDriver(p.preferences ?? Preferences())
        let reached = ParkItLevel.of(p.level)
        if let seen = session.lastSeenLevel, reached.minPoints > ParkItLevel.of(seen).minPoints, sheet == nil {
            sheet = .levelUp(reached)
        }
        session.lastSeenLevel = reached.rawValue
        let helpedBefore = session.lastSeenHelped
        session.lastSeenHelped = p.successfulReports
        if let helpedBefore, p.successfulReports > helpedBefore { show(L("report_helped_someone"), seconds: 8) }
    }

    func show(_ message: String, action: String? = nil, seconds: Double? = nil, _ run: (() -> Void)? = nil) {
        snackbar = Snackbar(message: message, actionLabel: action, action: run)
        snackTask?.cancel()
        snackTask = Task {
            try? await Task.sleep(for: .seconds(seconds ?? (action == nil ? 4 : 8)))
            if !Task.isCancelled { snackbar = nil }
        }
    }

    private func openSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    }

    // MARK: Data around the pin

    func targetChanged(debounced: Bool = true) {
        targetTasks.forEach { $0.cancel() }
        targetTasks.removeAll()
        let target = geocodeTarget
        if let hit = Geocoder.cached(lat: target.latitude, lng: target.longitude) {
            resolvedAddress = hit.shortLabel
            resolvedStreet = hit.streetLabel
            addressLoading = false
            addressFailed = hit.shortLabel == nil
        } else {
            addressLoading = true
            targetTasks.append(Task {
                try? await Task.sleep(for: .milliseconds(250)) // a quick follow-up drag cancels this
                guard !Task.isCancelled else { return }
                let r = await Geocoder.reverse(lat: target.latitude, lng: target.longitude)
                guard !Task.isCancelled else { return }
                resolvedAddress = r?.shortLabel
                resolvedStreet = r?.streetLabel
                addressFailed = r?.shortLabel == nil
                addressLoading = false
            })
        }
        // Spots load independently: a slow geocoder must never delay them.
        targetTasks.append(Task {
            if debounced { try? await Task.sleep(for: .milliseconds(300)) }
            await loadSpots(target)
        })
        targetTasks.append(Task {
            do {
                let f = try await api.areaForecast(lat: target.latitude, lng: target.longitude)
                guard !Task.isCancelled else { return }
                forecast = f
                forecastNoData = f == nil
            } catch {
                forecast = nil
                forecastNoData = false
            }
        })
        if showLots { loadLots() }
    }

    private func loadSpots(_ target: Coord) async {
        // No fix, no spots: the server only answers for a real position.
        guard let me = deviceLocation else {
            spots = []
            spotsLoaded = false
            spotsFailed = false
            return
        }
        while !Task.isCancelled {
            do {
                let result = try await api.nearbySpots(lat: target.latitude, lng: target.longitude, radiusM: MapConst.radiusM, me: me)
                guard !Task.isCancelled else { return }
                spots = result
                spotsLoaded = true
                spotsFailed = false
                return
            } catch {
                if Task.isCancelled || session.token == nil { return }
                spotsFailed = true
                try? await Task.sleep(for: .seconds(10)) // keep trying quietly; the sheet shows a retry button
            }
        }
    }

    func retrySpots() {
        spotsFailed = false
        targetChanged(debounced: false)
    }

    func refreshSpotsNow() {
        guard let me = deviceLocation else { return }
        let target = geocodeTarget
        Task {
            if let s = try? await api.nearbySpots(lat: target.latitude, lng: target.longitude, radiusM: MapConst.radiusM, me: me) { spots = s }
        }
    }

    private func loadLots() {
        let target = geocodeTarget
        Task {
            if let l = try? await api.parkingLots(lat: target.latitude, lng: target.longitude, radiusM: MapConst.lotsRadiusM) { lots = l }
        }
    }

    /// Street and number for listed spots, nearest first. Reports carry their own address;
    /// only older ones (and the English UI) need a lookup.
    private func resolveAddresses() {
        addressTask?.cancel()
        // Ones that already failed go last, so they can't starve the rest.
        let list = sortedSpots.filter { !spotAddressFailed.contains($0.id) } + sortedSpots.filter { spotAddressFailed.contains($0.id) }
        addressTask = Task {
            for spot in list where spot.localAddress == nil && spotAddresses[spot.id] == nil {
                if let hit = Geocoder.spot(spot.id) {
                    spotAddresses[spot.id] = hit
                    continue
                }
                let label = await Geocoder.resolve(spot: spot.id, lat: spot.lat, lng: spot.lng)
                if Task.isCancelled { return }
                if let label {
                    spotAddresses[spot.id] = label
                    spotAddressFailed.remove(spot.id)
                } else {
                    spotAddressFailed.insert(spot.id)
                }
                try? await Task.sleep(for: .milliseconds(150))
                if Task.isCancelled { return }
            }
        }
    }

    private func loadDailyTip(_ here: Coord) {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        let today = f.string(from: Date())
        guard session.tipShownDay != today, dailyTip == nil else { return }
        Task {
            if let tip = try? await api.bestHour(lat: here.latitude, lng: here.longitude) {
                dailyTip = tip
                session.tipShownDay = today
            }
        }
    }

    // MARK: Location

    func fly(to target: Coord, zoom: Double = MapConst.streetZoom) {
        mapView?.setCamera(MLNMapCamera(lookingAtCenter: target, altitude: altitude(zoom: zoom, at: target), pitch: 0, heading: mapView?.direction ?? 0), withDuration: 0.65, animationTimingFunction: nil)
    }

    private func altitude(zoom: Double, at c: Coord) -> CLLocationDistance {
        guard let map = mapView else { return 1000 }
        return MLNAltitudeForZoomLevel(zoom, 0, c.latitude, map.bounds.size)
    }

    private func fallback(_ messageKey: String? = nil) {
        if deviceLocation == nil, !userTookOver {
            let d = session.driver
            let target = d.homeLat.flatMap { lat in d.homeLng.map { Coord(latitude: lat, longitude: $0) } } ?? MapConst.telAviv
            geocodeTarget = target
            targetChanged()
            fly(to: target)
        }
        if let messageKey { show(L(messageKey)) }
    }

    func locationWhyContinue() {
        showLocationWhy = false
        manager.requestWhenInUseAuthorization()
    }

    func locationWhyNotNow() {
        showLocationWhy = false
        locating = false
        fallback()
    }

    func locateButton() {
        if hasPermission {
            locate(fromButton: true)
        } else if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else {
            show(L("location_denied"), action: L("location_open_settings")) { [weak self] in self?.openSettings() }
        }
    }

    /// Find the driver and fly there. On launch it doesn't override someone already
    /// panning; from the locate button it always flies.
    func locate(fromButton: Bool = false) {
        mapView?.showsUserLocation = true
        guard CLLocationManager.locationServicesEnabled() else {
            locating = false
            fallback()
            show(L("location_off"), action: L("location_turn_on")) { [weak self] in self?.openSettings() }
            return
        }
        pendingFly = true
        flyFromButton = fromButton
        locating = true
        manager.startUpdatingLocation()
        if let l = manager.location, Date().timeIntervalSince(l.timestamp) < 600, MapConst.inService(l.coordinate) { accept(l) }
        locateTimeout?.cancel()
        locateTimeout = Task {
            try? await Task.sleep(for: .seconds(8))
            guard !Task.isCancelled, pendingFly else { return }
            pendingFly = false
            locating = false
            if let here = deviceLocation {
                if fromButton { fly(to: here) }
            } else {
                fallback("map_location_unavailable")
            }
        }
    }

    private func accept(_ location: CLLocation) {
        let here = location.coordinate
        guard MapConst.inService(here) else {
            // ParkIt only operates in Israel; an out-of-country fix must not move the map.
            if pendingFly {
                pendingFly = false
                locating = false
                fallback("map_outside_service_area")
            }
            return
        }
        let firstKnown = deviceLocation == nil
        deviceLocation = here
        lastAccuracy = location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : nil
        session.lastFix = (here.latitude, here.longitude)
        if firstKnown { loadDailyTip(here) }
        checkArrival()
        // Moving about 100 m refreshes which spots are within reach.
        let newCell = String(format: "%.3f,%.3f", here.latitude, here.longitude)
        if newCell != cell {
            let hadCell = cell != nil
            cell = newCell
            if hadCell, !pendingFly { refreshSpotsNow() }
        }
        if pendingFly {
            pendingFly = false
            locating = false
            locateTimeout?.cancel()
            firstFix = here
            firstFixAt = Date()
            if flyFromButton || !userTookOver {
                goTo(here)
            } else if firstKnown {
                targetChanged(debounced: false) // the first fix is what lets spots load at all
            }
            loadDailyTip(here)
        } else if let first = firstFix, let at = firstFixAt, Date().timeIntervalSince(at) < 20, !userTookOver, here.distance(to: first) > 60 {
            // GPS precision arriving after the first quick fix: move only if meaningfully off.
            firstFix = nil
            goTo(here)
        }
    }

    private func goTo(_ here: Coord) {
        geocodeTarget = here
        targetChanged()
        fly(to: here)
    }

    /// Reports must be made near the spot (the server enforces the same limit):
    /// say so up front instead of after the photo.
    func openReportFlow() {
        if outsideTelAviv {
            show(L("map_tel_aviv_only"))
            return
        }
        guard let here = deviceLocation else {
            show(L("report_needs_location"))
            return
        }
        let limit = MapConst.maxReportM + min(lastAccuracy ?? 0, MapConst.maxAccuracyAllowanceM)
        if here.distance(to: geocodeTarget) > limit {
            show(L("report_too_far"), action: L("report_go_to_me")) { [weak self] in
                self?.goTo(here)
                self?.snackbar = nil
            }
        } else {
            sheet = .report
        }
    }

    func reportFields() -> (lat: Double?, lng: Double?, accuracy: Double?) {
        (deviceLocation?.latitude, deviceLocation?.longitude, lastAccuracy)
    }

    // MARK: The car

    func rememberCar(at: Coord, address: String?, spotType: String = "street") {
        clearHeading()
        if let left = leftSpot {
            leftSpot = nil
            let near = at.distance(to: Coord(latitude: left.car.lat, longitude: left.car.lng)) < 60
            if near, Date().timeIntervalSince(left.car.at) < 110 {
                Task {
                    if (try? await api.cancelSpot(left.id)) != nil { spots.removeAll { $0.id == left.id } }
                    await refreshMyProfile()
                }
            }
        }
        let car = ParkedCar(lat: at.latitude, lng: at.longitude, at: Date(), address: address, spotType: spotType)
        parkedCar = car
        resolveCarAddress()
    }

    /// The saved car's address, also for one saved before the app was closed.
    private func resolveCarAddress() {
        carAddressFailed = false
        guard let car = parkedCar, car.address == nil else { return }
        Task {
            let label = await Geocoder.reverse(lat: car.lat, lng: car.lng)?.shortLabel
            guard parkedCar?.at == car.at else { return }
            if let label { parkedCar?.address = label } else { carAddressFailed = true }
        }
    }

    private func offerPay() {
        if !ParkingPay.isOff { sheet = .parkedPay }
    }

    /// "I just parked": explained once, then one tap saves the car here.
    func parkHere() {
        guard session.carExplained else {
            sheet = .explainer
            return
        }
        confirmParked()
    }

    func confirmParked() {
        session.carExplained = true
        guard let here = deviceLocation else {
            sheet = nil
            show(L("report_needs_location"))
            return
        }
        rememberCar(at: here, address: nil)
        fly(to: here)
        sheet = nil
        offerPay()
    }

    func parkedOnSpot(_ spot: SpotOut, address: String?) {
        rememberCar(at: coord(spot), address: address, spotType: spot.spotType)
        offerPay()
    }

    func forgetCar() {
        parkedCar = nil
        ParkingReminder.cancel()
        let app = ParkingPay.session
        ParkingPay.clearSession()
        sheet = app.map { .stopPay($0) }
    }

    /// "I'm leaving": the driver's own spot becomes a report in one tap, with no photo:
    /// they're standing right at it (the distance check still runs).
    func leave(_ car: ParkedCar) {
        guard !handingOn else { return } // the answer can take a few seconds; a second tap adds nothing
        // Outside the launch city there's nobody to hand it to: just end the parking.
        if telAvivOnly, !MapConst.inTelAviv(Coord(latitude: car.lat, longitude: car.lng)) {
            forgetCar()
            return
        }
        handingOn = true
        Task {
            defer { handingOn = false }
            do {
                let spot = try await api.reportSpot(SpotCreate(
                    lat: car.lat, lng: car.lng, spotType: car.spotType, payment: "free",
                    vehicleSize: prefs.vehicleSize, address: car.address,
                    reporterLat: deviceLocation?.latitude, reporterLng: deviceLocation?.longitude,
                    reporterAccuracyM: lastAccuracy, handoff: true
                ))
                forgetCar()
                var was = car
                was.at = Date()
                leftSpot = (spot.id, was)
                refreshSpotsNow()
                await refreshMyProfile()
                show(L("car_leaving_thanks"))
            } catch {
                if error.isOutsideServiceArea { forgetCar() } else { show(friendly(error)) }
            }
        }
    }

    /// "I'm leaving" without a saved car: the spot is where the phone is now.
    func leaveHereNow() {
        guard let here = deviceLocation else {
            show(L("location_denied"))
            return
        }
        if outsideTelAviv { return }
        Task {
            do {
                _ = try await api.reportSpot(SpotCreate(
                    lat: here.latitude, lng: here.longitude, spotType: "street", payment: "free",
                    vehicleSize: prefs.vehicleSize,
                    reporterLat: here.latitude, reporterLng: here.longitude,
                    reporterAccuracyM: lastAccuracy, leaving: true
                ))
                refreshSpotsNow()
                await refreshMyProfile()
                show(L("map_reported_snackbar"))
            } catch {
                if !error.isOutsideServiceArea { show(friendly(error)) } // nothing to hand on here, and nothing to complain about
            }
        }
    }

    func onMain() {
        switch mainAction {
        case .leave:
            if let car = parkedCar { leave(car) }
        case .reminder:
            sheet = .car
        case .report:
            openReportFlow()
        }
    }

    // MARK: Trip

    func claimed(_ spot: SpotOut) {
        sheet = nil
        let end = Date().addingTimeInterval(MapConst.claimHold)
        session.claimEndsAt = end
        claimEndsAt = end
        activeClaim = spot
        session.headingTo = spot
        session.headingSince = Date()
        refreshSpotsNow()
        // After the spot's card has gone, or the question would have nowhere to show.
        Task {
            try? await Task.sleep(for: .milliseconds(900))
            checkArrival()
        }
    }

    private func clearHeading() {
        session.headingTo = nil
        session.headingSince = nil
        askArrived = false
    }

    func checkArrival() {
        guard let spot = session.headingTo, let since = session.headingSince else { return }
        if Date().timeIntervalSince(since) > MapConst.arrivalPrompt {
            clearHeading()
        } else if let here = deviceLocation, here.distance(to: coord(spot)) <= MapConst.arrivedM {
            askArrived = true
        }
    }

    func arrivedYes() {
        guard let spot = session.headingTo else { return }
        Task {
            // The hold may be long over; the car is saved either way.
            try? await api.submitFeedback(spot.id, "confirmed_taken")
            rememberCar(at: coord(spot), address: spot.localAddress ?? spot.address, spotType: spot.spotType)
            activeClaim = nil
            refreshSpotsNow()
            offerPay()
        }
    }

    func arrivedNo() { clearHeading() }

    func releaseClaim() {
        guard let claim = activeClaim else { return }
        activeClaim = nil
        clearHeading()
        Task {
            try? await api.releaseClaim(claim.id)
            refreshSpotsNow()
        }
    }

    func tripFeedback(_ type: String) {
        guard let claim = activeClaim else { return }
        Task {
            do {
                let parked = type == "confirmed_taken"
                do {
                    try await api.submitFeedback(claim.id, type)
                } catch let e as APIError where e.status == 409 && parked {
                    // Already voted or closed: the driver still parked here.
                }
                if parked { rememberCar(at: coord(claim), address: claim.localAddress, spotType: claim.spotType) }
                activeClaim = nil
                refreshSpotsNow()
                if parked { offerPay() } else { show(L("claim_taken_thanks")) }
            } catch {
                show(friendly(error))
            }
        }
    }

    func claimExpired() {
        activeClaim = nil
        show(L("claim_expired"))
        refreshSpotsNow()
    }

    // MARK: Map interaction

    func cameraWillMove(byGesture: Bool) {
        if byGesture, locating { userTookOver = true }
        cameraMoving = true
        addressLoading = true
    }

    func cameraIdle(center: Coord) {
        cameraMoving = false
        // While still finding the driver, the overview camera isn't a place anyone wants spots for.
        if !locating || userTookOver {
            if abs(center.latitude - geocodeTarget.latitude) < 1e-6, abs(center.longitude - geocodeTarget.longitude) < 1e-6 {
                addressLoading = false
                if !targetTasks.isEmpty, Geocoder.cached(lat: center.latitude, lng: center.longitude) == nil { addressLoading = resolvedAddress == nil && !addressFailed }
            } else {
                geocodeTarget = center
                targetChanged()
            }
        } else {
            addressLoading = false
        }
    }

    func selectSpot(id: String) {
        if let s = visibleSpots.first(where: { $0.id == id }) { sheet = .spot(s) }
    }

    func selectLot(id: Int) {
        if let l = lots.first(where: { $0.id == id }) { sheet = .lot(l) }
    }

    // MARK: Pushing data into the map

    private var selectedID: String? {
        if case .spot(let s) = sheet { return s.id }
        return nil
    }

    private func pushAll() {
        pushSpots()
        pushLots()
        pushCar()
    }

    private func pushSpots() {
        guard let style, let source = style.source(withIdentifier: ParkMapView.sourceSpots) as? MLNShapeSource else { return }
        let features: [MLNPointFeature] = visibleSpots.map { spot in
            let color = pinColor(spot)
            let tag = relativeShort(spot.reportedAt)
            let disabled = spot.spotType == "disabled"
            let hot = spot.hotMinutes != nil && spot.status == "active"
            let frame = ParkItLevel.of(spot.reporterLevel).pinFrame
            let icon = "pin-\(color)-\(tag)-\(disabled ? "d" : "p")\(hot ? "-hot" : "")-\(frame ?? "w")"
            if style.image(forName: icon) == nil {
                style.setImage(Pins.pin(color: color, tag: tag, disabled: disabled, hot: hot, frame: frame), forName: icon)
            }
            let f = MLNPointFeature()
            f.coordinate = coord(spot)
            f.attributes = ["id": spot.id, "icon": icon, "age": ageMinutes(spot.reportedAt)]
            return f
        }
        source.shape = MLNShapeCollectionFeature(shapes: features)
        if let layer = style.layer(withIdentifier: ParkMapView.layerPins) as? MLNSymbolStyleLayer {
            layer.iconScale = NSExpression(format: "TERNARY(id == %@, 1.25, 1)", selectedID ?? "")
        }
    }

    private func pushLots() {
        guard let source = style?.source(withIdentifier: ParkMapView.sourceLots) as? MLNShapeSource else { return }
        let features: [MLNPointFeature] = showLots ? lots.map { lot in
            let f = MLNPointFeature()
            f.coordinate = Coord(latitude: lot.lat, longitude: lot.lng)
            f.attributes = ["id": lot.id]
            return f
        } : []
        source.shape = MLNShapeCollectionFeature(shapes: features)
    }

    func pushCar() {
        guard let style, let source = style.source(withIdentifier: ParkMapView.sourceCar) as? MLNShapeSource else { return }
        style.setImage(Pins.carMarker(kind: prefs.vehicleSize, paint: myCarPaint(registry: prefs.carPaint), special: session.carSpecial), forName: ParkMapView.carIcon)
        let features: [MLNPointFeature] = parkedCar.map { car in
            let f = MLNPointFeature()
            f.coordinate = Coord(latitude: car.lat, longitude: car.lng)
            return [f]
        } ?? []
        source.shape = MLNShapeCollectionFeature(shapes: features)
    }
}

extension MapModel: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        let status = m.authorizationStatus
        MainActor.assumeIsolated {
            guard started else { return }
            switch status {
            case .authorizedWhenInUse, .authorizedAlways:
                showLocationWhy = false
                if deviceLocation == nil || pendingFly || locating { locate() }
            case .denied, .restricted:
                locating = false
                pendingFly = false
                fallback()
                show(L("location_denied"), action: L("location_open_settings")) { [weak self] in self?.openSettings() }
            default:
                break
            }
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let last = locations.last else { return }
        MainActor.assumeIsolated { accept(last) }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {}
}

// Google-Maps-adjacent status colours: green = go, brand blue = yours, amber = someone's on the way.
func pinColor(_ spot: SpotOut) -> String {
    if spot.status == "claimed" { return "#E8710A" }
    if spot.mine { return "#1B4F91" }
    if spot.reporterLevel == "legend" { return "#7B3FE4" }
    // A free spot starts deep green and pales as it waits, in five steps the eye can tell apart.
    switch ageMinutes(spot.reportedAt) {
    case ..<2: return "#0B5E2B"
    case ..<4: return "#188038"
    case ..<6: return "#2E9E4F"
    case ..<8: return "#55B56E"
    default: return "#7EC791"
    }
}
