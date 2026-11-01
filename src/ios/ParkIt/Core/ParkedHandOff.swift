import ActivityKit
import CoreLocation
import Foundation

extension Notification.Name {
    /// The lock screen ended the parking while the map may be in memory.
    static let parkedCarChanged = Notification.Name("parkedCarChanged")
}

/// The Live Activity for the parked car (remind/ParkedNotification.kt on Android).
@MainActor
enum ParkedActivity {
    private static var current: Activity<ParkedAttributes>? { Activity<ParkedAttributes>.activities.first }

    /// Follows the saved car: starts, refreshes the address, or ends without a trace.
    static func sync(_ car: ParkedCar?) {
        guard let car else {
            Task { await endAll() }
            return
        }
        let state = ParkedAttributes.ContentState(title: L("parked_notification_title"), message: car.address, canLeave: true)
        if let current {
            Task { await current.update(ActivityContent(state: state, staleDate: nil)) }
        } else if ActivityAuthorizationInfo().areActivitiesEnabled {
            let attributes = ParkedAttributes(leaveLabel: L("dock_leave"), rightToLeft: L10n.isHebrew)
            _ = try? Activity.request(attributes: attributes, content: ActivityContent(state: state, staleDate: nil))
        }
    }

    static func problem(_ message: String) async {
        let state = ParkedAttributes.ContentState(title: L("parked_notification_title"), message: message, canLeave: true)
        await current?.update(ActivityContent(state: state, staleDate: nil))
    }

    static func finished(_ message: String) async {
        let state = ParkedAttributes.ContentState(title: L("parked_notification_thanks"), message: message, canLeave: false)
        await current?.end(ActivityContent(state: state, staleDate: nil), dismissalPolicy: .after(Date().addingTimeInterval(60)))
    }

    private static func endAll() async {
        for activity in Activity<ParkedAttributes>.activities where activity.activityState == .active {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }
}

/// What "I'm leaving" on the lock screen does, with the same outcomes as Android's
/// LeaveSpotReceiver: thanks, a stop-paying reminder, too far, or try again.
@MainActor
enum ParkedHandOff {
    static func run() async {
        let session = Session.shared
        guard let car = session.parkedCar else {
            ParkedActivity.sync(nil)
            return
        }
        let fix = CLLocationManager().location
        do {
            _ = try await API.shared.reportSpot(SpotCreate(
                lat: car.lat, lng: car.lng, spotType: car.spotType, payment: "free",
                vehicleSize: session.driver.preferences.vehicleSize, address: car.address,
                reporterLat: fix?.coordinate.latitude, reporterLng: fix?.coordinate.longitude,
                reporterAccuracyM: fix.flatMap { $0.horizontalAccuracy >= 0 ? $0.horizontalAccuracy : nil },
                handoff: true
            ))
        } catch {
            let far = (error as? APIError)?.status == 422
            await ParkedActivity.problem(L(far ? "parked_notification_far" : "parked_notification_failed"))
            return
        }
        session.parkedCar = nil
        ParkingReminder.cancel()
        let paying = ParkingPay.session != nil
        ParkingPay.clearSession()
        await ParkedActivity.finished(L(paying ? "parked_notification_stop_paying" : "parked_notification_done"))
        NotificationCenter.default.post(name: .parkedCarChanged, object: nil)
    }
}
