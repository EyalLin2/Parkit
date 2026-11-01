import CoreLocation
import XCTest
@testable import ParkIt

/// What the lock-screen "I'm leaving" action runs, against the real backend: every outcome
/// that remind/ParkedNotification.kt has on Android. iOS can't build a notification response
/// in a test, so these start one call below the delegate, which only forwards to this.
@MainActor
final class ParkedHandOffTests: XCTestCase {
    /// Away from the test location of the flows that run after these, so their map stays as seeded.
    private let spot = CLLocationCoordinate2D(latitude: 32.0700, longitude: 34.7700)
    private let realBase = ApiConfig.baseURL

    override func setUp() async throws {
        ApiConfig.baseURL = realBase
        ParkingPay.clearSession()
        let session = Session.shared
        session.signOut()
        // Each test is a new driver: the server allows one report per 30 s.
        let body = try JSONSerialization.data(withJSONObject: [
            "auth_provider": "dev", "external_id": "e2e-unit-\(UUID().uuidString)", "display_name": "Unit",
        ])
        var request = URLRequest(url: URL(string: realBase + "auth/dev-login")!)
        request.httpMethod = "POST"
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let (data, _) = try await URLSession.shared.data(for: request)
        let login = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let token = try XCTUnwrap(login?["access_token"] as? String)
        let id = try XCTUnwrap(login?["user_id"] as? String)
        session.save(token: token, userId: id, displayName: "Unit")
        session.parkedCar = ParkedCar(lat: spot.latitude, lng: spot.longitude, at: Date(), address: "Dizengoff")
    }

    override func tearDown() async throws {
        ApiConfig.baseURL = realBase
        ParkingPay.clearSession()
        Session.shared.signOut()
    }

    private func here() -> CLLocation { CLLocation(latitude: spot.latitude, longitude: spot.longitude) }

    func testHandsTheSpotOnAndThanks() async throws {
        let ended = expectation(forNotification: .parkedCarChanged, object: nil)
        let outcome = await ParkedHandOff.run(fix: here())
        XCTAssertEqual(outcome, .thanks)
        await fulfillment(of: [ended], timeout: 1)
        XCTAssertNil(Session.shared.parkedCar)
        XCTAssertNil(ParkingReminder.activeChangeAt)
        // The server has it, marked as left by a driver and as this driver's.
        let spots = try await API.shared.nearbySpots(lat: spot.latitude, lng: spot.longitude, radiusM: 100, me: spot)
        let reported = try XCTUnwrap(spots.first { $0.mine })
        XCTAssertTrue(reported.left)
    }

    func testRemindsToStopPayingAfterAPaidParking() async {
        UserDefaults.standard.set(ParkingPay.pango.rawValue, forKey: "pay_session_app")
        let outcome = await ParkedHandOff.run(fix: here())
        XCTAssertEqual(outcome, .stopPaying)
        XCTAssertNil(Session.shared.parkedCar)
        XCTAssertNil(ParkingPay.session)
    }

    func testTooFarKeepsTheCar() async {
        // About 2.7 km from the car: over the server's 150 m rule.
        let outcome = await ParkedHandOff.run(fix: CLLocation(latitude: 32.0950, longitude: 34.7700))
        XCTAssertEqual(outcome, .tooFar)
        XCTAssertNotNil(Session.shared.parkedCar)
    }

    func testServerUnreachableAsksToTryAgainAndKeepsTheCar() async {
        ApiConfig.baseURL = "http://localhost:9/"
        let outcome = await ParkedHandOff.run(fix: here())
        XCTAssertEqual(outcome, .tryAgain)
        XCTAssertNotNil(Session.shared.parkedCar)
    }

    func testNoSavedCarDoesNothing() async {
        Session.shared.parkedCar = nil
        let outcome = await ParkedHandOff.run(fix: here())
        XCTAssertEqual(outcome, .noCar)
    }
}
