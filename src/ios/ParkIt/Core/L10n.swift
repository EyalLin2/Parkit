import Foundation
import SwiftUI
import UIKit

/// The in-app language: Hebrew by default, English on request. The strings come
/// straight from the Android app (scripts/sync_android_resources.py).
enum L10n {
    private static let key = "parkit_language"
    private static var tables: [String: Bundle] = [:]

    static var language: String = {
        let saved = UserDefaults.standard.string(forKey: key)
        return saved == "en" ? "en" : "he"
    }()

    static var isHebrew: Bool { language == "he" }
    static var layoutDirection: LayoutDirection { isHebrew ? .rightToLeft : .leftToRight }
    static var locale: Locale { Locale(identifier: isHebrew ? "he_IL" : "en_IL") }

    static func set(_ code: String) {
        language = code == "en" ? "en" : "he"
        UserDefaults.standard.set(language, forKey: key)
        applySystem()
    }

    /// UIKit pieces (alerts, keyboards, permission prompts) take their direction and
    /// language from these, not from the SwiftUI environment.
    static func applySystem() {
        UserDefaults.standard.set([language], forKey: "AppleLanguages")
        let direction: UISemanticContentAttribute = isHebrew ? .forceRightToLeft : .forceLeftToRight
        UIView.appearance().semanticContentAttribute = direction
    }

    private static func bundle(_ code: String) -> Bundle? {
        if let b = tables[code] { return b }
        guard let path = Bundle.main.path(forResource: code, ofType: "lproj"), let b = Bundle(path: path) else { return nil }
        tables[code] = b
        return b
    }

    static func raw(_ key: String) -> String? {
        let missing = "\u{1}"
        for code in [language, "en"] {
            if let v = bundle(code)?.localizedString(forKey: key, value: missing, table: nil), v != missing { return v }
        }
        return nil
    }
}

/// A translated string, formatted like Android's getString.
func L(_ key: String, _ args: CVarArg...) -> String {
    let s = L10n.raw(key) ?? key
    return args.isEmpty ? s : String(format: s, locale: Locale(identifier: "en_US_POSIX"), arguments: args)
}

/// A translated plural, formatted with `count`.
func LP(_ key: String, _ count: Int) -> String {
    let category: String
    switch count {
    case 1: category = "one"
    case 2: category = "two"
    default: category = "other"
    }
    let s = L10n.raw("\(key)#\(category)") ?? L10n.raw("\(key)#other") ?? key
    return String(format: s, locale: Locale(identifier: "en_US_POSIX"), arguments: [count])
}

/// Keeps a user-written name whole inside the other direction's text.
func isolate(_ text: String) -> String { "\u{2068}\(text)\u{2069}" }
