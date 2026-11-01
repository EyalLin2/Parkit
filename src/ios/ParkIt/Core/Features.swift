/// What's switched on (Features.kt). Off means out of the way, not deleted.
enum Features {
    // The first run stays short: welcome, sign-in, name, map.

    /// The four slides after the welcome page.
    static let introSlides = false

    /// "Your car" after sign-up. Still in Profile and Settings.
    static let firstRunCarStep = false

    /// "How ParkIt works" after sign-up. Still in Settings.
    static let firstRunTips = false

    /// The avatar grid on the first details screen. Still in Profile.
    static let firstRunAvatar = false

    // Extras kept out of Profile and Rewards for now.

    /// The Partners tab in Rewards, which also lists a driver's codes.
    static let partners = false

    /// The weekly league in Profile.
    static let league = false

    /// The badges page and its row in Profile.
    static let badges = false
}
