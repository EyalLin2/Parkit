import CoreLocation
import Foundation
import UIKit
import UserNotifications

extension Notification.Name {
    /// The lock screen ended the parking while the map may be in memory.
    static let parkedCarChanged = Notification.Name("parkedCarChanged")
}

/// While the car is parked: a quiet notification whose "I'm leaving" action hands the spot
/// on without opening the app (remind/ParkedNotification.kt). iOS has no ongoing
/// notifications, so it is posted again each time the app goes to the background: that puts
/// it on the lock screen right when the phone is put away.
@MainActor
enum ParkedNotification {
    static let id = "parked-car"
    private static let category = "parked"
    static let leaveAction = "leave"

    private static var center: UNUserNotificationCenter { .current() }

    private static func registerCategory() {
        let leave = UNNotificationAction(identifier: leaveAction, title: L("dock_leave"), options: [])
        center.setNotificationCategories([UNNotificationCategory(identifier: category, actions: [leave], intentIdentifiers: [], options: [])])
    }

    /// Follows the saved car, asking for permission the first time one is saved.
    static func sync(_ car: ParkedCar?) {
        guard let car else {
            cancel()
            return
        }
        Task {
            if await center.notificationSettings().authorizationStatus == .notDetermined {
                _ = try? await center.requestAuthorization(options: [.alert, .sound])
            }
            show(car)
        }
    }

    static func show(_ car: ParkedCar, message: String? = nil, delay: TimeInterval? = nil) {
        registerCategory()
        let content = UNMutableNotificationContent()
        content.title = L("parked_notification_title")
        content.body = message ?? car.address ?? ""
        content.categoryIdentifier = category
        let trigger = delay.map { UNTimeIntervalNotificationTrigger(timeInterval: $0, repeats: false) }
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    static func finished(_ message: String) {
        center.removeDeliveredNotifications(withIdentifiers: [id])
        let content = UNMutableNotificationContent()
        content.title = L("parked_notification_thanks")
        content.body = message
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
    }

    static func cancel() {
        center.removePendingNotificationRequests(withIdentifiers: [id])
        center.removeDeliveredNotifications(withIdentifiers: [id])
    }
}

/// What "I'm leaving" on the notification does, with the same outcomes as Android's
/// LeaveSpotReceiver: thanks, a stop-paying reminder, too far, or try again.
@MainActor
enum ParkedHandOff {
    enum Outcome { case thanks, stopPaying, tooFar, tryAgain, noCar }

    @discardableResult
    static func run(fix: CLLocation? = CLLocationManager().location) async -> Outcome {
        let session = Session.shared
        guard let car = session.parkedCar else {
            ParkedNotification.cancel()
            return .noCar
        }
        do {
            _ = try await API.shared.reportSpot(SpotCreate(
                lat: car.lat, lng: car.lng, spotType: car.spotType, payment: "free",
                vehicleSize: session.driver.preferences.vehicleSize, address: car.address,
                reporterLat: fix?.coordinate.latitude, reporterLng: fix?.coordinate.longitude,
                reporterAccuracyM: fix.flatMap { $0.horizontalAccuracy >= 0 ? $0.horizontalAccuracy : nil },
                handoff: true
            ))
        } catch {
            // Outside the launch city there's nobody to hand it to: just end the parking.
            if error.isOutsideServiceArea {
                session.parkedCar = nil
                ParkingReminder.cancel()
                let paying = ParkingPay.session != nil
                ParkingPay.clearSession()
                if paying {
                    ParkedNotification.finished(L("parked_notification_ended_stop_paying"))
                } else {
                    ParkedNotification.cancel()
                }
                NotificationCenter.default.post(name: .parkedCarChanged, object: nil)
                return paying ? .stopPaying : .noCar
            }
            let far = (error as? APIError)?.status == 422
            ParkedNotification.show(car, message: L(far ? "parked_notification_far" : "parked_notification_failed"))
            return far ? .tooFar : .tryAgain
        }
        session.parkedCar = nil
        ParkingReminder.cancel()
        let paying = ParkingPay.session != nil
        ParkingPay.clearSession()
        ParkedNotification.finished(L(paying ? "parked_notification_stop_paying" : "parked_notification_done"))
        NotificationCenter.default.post(name: .parkedCarChanged, object: nil)
        return paying ? .stopPaying : .thanks
    }
}

/// Receives the notification's action, also when iOS starts the app in the background for it.
final class ParkItAppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// In the app it stays quiet: the map already shows the car.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        notification.request.identifier == "parked-car" ? [.list] : [.banner, .list, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard response.actionIdentifier == "leave" else { return }
        await ParkedHandOff.run()
    }
}
