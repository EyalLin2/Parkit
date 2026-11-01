package com.parkit.app

/** Finished features kept out of the app for now. Flip one to bring it back. */
object Features {
    /** Launch is email only: each SMS costs money and a phone isn't a bulk gateway. */
    const val SMS_LOGIN = false

    /** Never tested in a real car, and it asks for sensitive permissions. */
    const val CAR_BLUETOOTH = false

    /** One AI call per report for little benefit. */
    const val PHOTO_HINTS = false

    // The first run stays short: welcome, sign-in, name, map.

    /** The four slides after the welcome page. */
    const val INTRO_SLIDES = false

    /** "Your car" after sign-up. Still in Profile and Settings. */
    const val FIRST_RUN_CAR_STEP = false

    /** "How ParkIt works" after sign-up. Still in Settings. */
    const val FIRST_RUN_TIPS = false

    /** The avatar grid on the first details screen. Still in Profile. */
    const val FIRST_RUN_AVATAR = false

    // Extras kept out of Profile and Rewards for now.

    /** The Partners tab in Rewards, which also lists a driver's codes. */
    const val PARTNERS = false

    /** The weekly league in Profile. */
    const val LEAGUE = false

    /** The badges page and its row in Profile. */
    const val BADGES = false

    /** The full-screen ad when the map opens. */
    const val OPEN_AD = false
}
