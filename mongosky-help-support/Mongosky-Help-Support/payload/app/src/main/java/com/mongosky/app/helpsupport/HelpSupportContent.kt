package com.mongosky.app.helpsupport

/** Curated offline help. Update answers here without changing navigation or UI. */
internal object HelpSupportContent {
    const val CONTACT_EMAIL = "mongosky0@gmail.com"
    val articles: List<HelpArticle> = listOf(
        HelpArticle("sign-in", HelpTopic.ACCOUNT, "I can't sign in. What should I do?",
            "Check that you are using the email address registered with your account. If you forgot your password, use Forgot password on the sign-in page. If you still need help, contact support and select Account help."),
        HelpArticle("reset-password", HelpTopic.ACCOUNT, "How do I reset my password?",
            "On the sign-in page, tap Forgot password and enter your account email. Follow the verification steps to set a new password. Check your spam folder if the email does not arrive. Never share the verification code with anyone."),
        HelpArticle("profile", HelpTopic.ACCOUNT, "How do I update my profile?",
            "Open your own Profile and tap Edit profile. Update the available details and save your changes. If a change does not appear, reopen your profile after checking your connection."),
        HelpArticle("edit-post", HelpTopic.POSTS, "How do I edit or delete my post?",
            "Find a post you created and open its three-dot menu. Choose Edit to make changes or Delete to remove it. You can manage your own posts; another person's posts cannot be edited from your account."),
        HelpArticle("upload", HelpTopic.POSTS, "My photo or video won't upload.",
            "Check your internet connection and allow access to the photo or video you selected. Try the upload again using a supported file from your phone. If it keeps failing, select Report a problem and describe what happened and any error shown."),
        HelpArticle("safety-report", HelpTopic.SAFETY, "How do I report harmful content or impersonation?",
            "Contact support and choose Privacy & safety. Include the profile name, a post or profile link if you have one, and a clear description. You can add a screenshot in your email app before sending. Share only the information needed to explain the issue."),
        HelpArticle("secure-account", HelpTopic.SAFETY, "How can I keep my account secure?",
            "Use a strong password that you do not use on other websites. Keep your account email secure and do not share passwords or verification codes. If you suspect someone else can access your account, reset your password and contact support."),
        HelpArticle("privacy", HelpTopic.SAFETY, "Where can I read the privacy policy?",
            "Open Settings and select Privacy Policy. For a question about your personal data or privacy, contact support and choose Privacy & safety."),
        HelpArticle("notifications", HelpTopic.APP, "Why am I not receiving notifications?",
            "In your phone's Android settings, open Apps, select Mongosky, and check Notifications. Allow the notifications you want to receive. Also check Do Not Disturb, your connection, and any battery restrictions on the app."),
        HelpArticle("feed", HelpTopic.APP, "My feed is not showing recent updates.",
            "Check your internet connection, reopen the feed, and refresh it. If the issue continues, close and reopen Mongosky. Use Report a problem to tell us which screen is affected and what you expected to see."),
        HelpArticle("app-problem", HelpTopic.APP, "The app is slow or closes unexpectedly.",
            "Close and reopen the app and check that your phone has free storage. Install an app update if one is available. If it happens again, use Report a problem and include the steps that caused it. You can choose to include the app and Android versions."),
        HelpArticle("feedback", HelpTopic.APP, "How can I suggest a new feature?",
            "Use Send feedback below. Tell us what you would like to do and how the change would help. Your message opens in your email app, where you can review and send it to Mongosky support.")
    )
}
