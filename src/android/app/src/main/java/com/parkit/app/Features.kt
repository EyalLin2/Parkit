package com.parkit.app

/** Finished features kept out of the app for now. Flip one to bring it back. */
object Features {
    /** Launch is email only: each SMS costs money and a phone isn't a bulk gateway. */
    const val SMS_LOGIN = false

    /** Never tested in a real car, and it asks for sensitive permissions. */
    const val CAR_BLUETOOTH = false

    /** One AI call per report for little benefit. */
    const val PHOTO_HINTS = false
}
