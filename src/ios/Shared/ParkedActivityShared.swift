import ActivityKit
import AppIntents
import Foundation
import os

/// What the parked-car Live Activity shows. The app fills in the wording in the language
/// chosen inside it, because an extension only knows the phone's language.
struct ParkedAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var title: String
        var message: String?
        /// Off once the spot was handed on: the card only says thanks.
        var canLeave: Bool
    }

    var leaveLabel: String
    var rightToLeft: Bool
}

/// "I'm leaving" on the lock screen: hands the spot on without opening the app. As a
/// LiveActivityIntent it runs in the app's process, which holds the session; the extension
/// only compiles a stub of ParkedHandOff.
struct LeaveSpotIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "I'm leaving"
    static var openAppWhenRun = false

    init() {}

    func perform() async throws -> some IntentResult {
        Logger(subsystem: "com.parkit.israel", category: "live").notice("LeaveSpotIntent performed in \(Bundle.main.bundleIdentifier ?? "?", privacy: .public)")
        await ParkedHandOff.run()
        return .result()
    }
}
