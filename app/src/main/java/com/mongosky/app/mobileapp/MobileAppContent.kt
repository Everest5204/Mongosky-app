package com.mongosky.app.mobileapp

// Content mirrors the launched web MobileApp/mobileAppContent.ts.
internal data class MobileAppPlatform(val id: String, val eyebrow: String, val title: String, val description: String, val paragraphs: List<String>, val status: String)
internal object MobileAppContent {
    const val heroEyebrow = "Mobile experience in progress"
    const val heroTitle = "Mongosky is coming to your phone"
    const val heroDescription = "Our mobile app is currently under development. Our developers are working carefully to create a fast, secure, and seamless experience for both Android and iOS users."
    const val heroSupportingText = "We are completing the important development and testing work now, and we hope to bring the app to you within the coming days."
    const val heroStatusLabel = "Development in progress"
    const val introductionEyebrow = "Built with care"
    const val introductionTitle = "A better mobile experience is on the way"
    const val introductionDescription = "The mobile app is being designed to make connecting, sharing, discovering content, and communicating with your community feel natural from anywhere."
    val introductionParagraphs = listOf("Our development team is currently working on performance, real-time updates, account security, reliable notifications, and a clean mobile experience.", "Before release, the app will go through careful testing across different devices and screen sizes. This helps us deliver a stable experience instead of releasing an unfinished product.", "Android and iOS development is progressing together so that users on both platforms can enjoy a consistent and professional experience.")
    const val closingEyebrow = "Launching soon"
    const val closingTitle = "Thank you for waiting with us"
    const val closingDescription = "Our developers are actively working on both versions of the app. We appreciate your patience while we complete the final development and testing needed for a reliable launch."
    const val closingSupportingText = "Official Android and iOS download information will be announced as soon as the apps are ready."
    const val closingBackButtonLabel = "Return to previous page"
    val platforms = listOf(
        MobileAppPlatform("android-app", "Android application", "The Android app is coming soon", "Our Android version is being developed for a smooth, responsive, and dependable experience across modern Android phones.", listOf("The development team is optimising the app for different screen sizes, device capabilities, and network conditions.", "Important areas such as real-time content, media performance, navigation, notifications, privacy, and account security are currently being tested.", "Once the Android version meets our quality and stability standards, official installation information will be announced here."), "Android version in development"),
        MobileAppPlatform("ios-app", "iOS application", "The iOS app is also on the way", "The iOS version is being created to provide iPhone users with the same fast, secure, and polished Mongosky experience.", listOf("Our developers are refining the interface, gestures, navigation, media experience, notifications, and overall performance for iOS devices.", "The app will be reviewed and tested carefully before release to ensure that it feels stable, intuitive, and consistent with the platform.", "Official App Store availability will be shared only after the iOS version is fully prepared for release."), "iOS version in development"),
    )
}
