package com.mongosky.app.privacypolicy

import com.mongosky.app.settingsinformation.*

// Exact published web copy. Keep policy text and dates in sync with the web source.
internal object PrivacyPolicyContent {
    val document = SettingsDocument(
        title = "Privacy Policy",
        paragraphs = listOf("Mongosky respects your privacy and is committed to handling your personal information responsibly.", "This Privacy Policy explains what information Mongosky collects, why it is used, when it may be shared, how it is protected, and the choices available to you.", "By creating an account or using Mongosky, you acknowledge the practices described in this Privacy Policy."),
        lastUpdated = "Last updated: September 1, 2026",
        blocks = listOf(
            SettingsDocumentBlock.Section(
                id = "policy-scope", title = "1. What This Policy Covers",
                paragraphs = listOf("This policy applies to Mongosky websites, social features, profiles, posts, comments, reactions, reels, messaging features, AI features, account services, and other related tools provided by Mongosky.", "Third-party websites and services have their own privacy practices. Their handling of information is governed by their respective privacy policies."),
            ),
            SettingsDocumentBlock.Section(
                id = "information-we-collect", title = "2. Information We Collect",
                paragraphs = listOf("The information Mongosky processes depends on how you use the platform and which features you choose to use."),
                points = listOf(
                    SettingsDocumentPoint("Account information", "Your first name, last name, email address or phone number, gender, date of birth, password credentials, account creation date, and account identifiers."),
                    SettingsDocumentPoint("Google sign-in information", "If you choose Google sign-in, Mongosky may receive your name and email address from Google to identify or create your account."),
                    SettingsDocumentPoint("Profile information", "Your profile image, cover image, follower and following information, post count, and other details you add to your profile."),
                    SettingsDocumentPoint("Content you provide", "Text posts, captions, images, videos, reels, comments, replies, and other content you upload, create, or share."),
                    SettingsDocumentPoint("Activity and connections", "Follows, reactions, comment activity, content interactions, and information necessary to display and manage your connections."),
                    SettingsDocumentPoint("Messages and AI conversations", "Messages, prompts, responses, conversation titles, and related timestamps when you use messaging or Mongosky AI features."),
                    SettingsDocumentPoint("Media information", "Uploaded file URLs, media identifiers, file types, display order, and information required to store and deliver images or videos."),
                    SettingsDocumentPoint("Technical information", "Basic request, browser, device, network, timestamp, error, and security information that may be processed when you connect to Mongosky."),
                ),
            ),
            SettingsDocumentBlock.Illustration("data-protection-illustration", "data-protection", "Your information deserves careful protection", "Mongosky uses account controls, protected credentials, authenticated requests, and responsible data-handling practices to help safeguard your information."),
            SettingsDocumentBlock.Section(
                id = "how-we-use-information", title = "3. How We Use Information",
                points = listOf(
                    SettingsDocumentPoint("Provide the platform", "To create accounts, authenticate users, display profiles, publish content, deliver media, and operate social features."),
                    SettingsDocumentPoint("Personalize your experience", "To show relevant profiles, posts, reels, connections, interactions, and feature states."),
                    SettingsDocumentPoint("Maintain security", "To protect accounts, identify suspicious activity, prevent misuse, and enforce platform rules."),
                    SettingsDocumentPoint("Provide support", "To respond to questions, investigate reported problems, recover accounts, and resolve technical issues."),
                    SettingsDocumentPoint("Improve Mongosky", "To understand feature performance, correct errors, improve reliability, and develop safer and more useful services."),
                    SettingsDocumentPoint("Meet legal responsibilities", "To comply with applicable laws, lawful requests, and obligations involving safety, fraud prevention, and platform integrity."),
                ),
            ),
            SettingsDocumentBlock.Section(
                id = "visibility-and-sharing", title = "4. How Information Is Visible or Shared",
                paragraphs = listOf("Mongosky is a social platform. Information you publish may be visible to other people depending on the feature and the privacy controls available to you.", "Your name, profile image, cover image, posts, comments, reactions, follower relationships, and other public activity may be shown to other users."),
                points = listOf(
                    SettingsDocumentPoint("With other users", "Information is displayed when necessary to provide profiles, feeds, comments, reactions, follows, messaging, and other social features."),
                    SettingsDocumentPoint("With service providers", "Information may be processed by trusted providers that help Mongosky deliver hosting, databases, media storage, authentication, email, security, and AI services."),
                    SettingsDocumentPoint("For legal or safety reasons", "Information may be disclosed when reasonably necessary to comply with law, respond to valid legal requests, protect users, investigate abuse, or defend the rights and safety of Mongosky and others."),
                    SettingsDocumentPoint("During a business change", "Information may be transferred as part of a merger, acquisition, restructuring, financing, or transfer of the platform, subject to appropriate safeguards."),
                ),
            ),
            SettingsDocumentBlock.Section(
                id = "third-party-services", title = "5. Third-Party Services",
                paragraphs = listOf("Mongosky uses selected third-party services to provide specific platform functions. These services may process limited information required to perform their work."),
                points = listOf(
                    SettingsDocumentPoint("Cloudinary", "Uploaded profile images, cover images, post images, and videos may be processed and stored through Cloudinary."),
                    SettingsDocumentPoint("Google authentication", "If you use Google sign-in, Google processes the authentication request and provides the permitted account information to Mongosky."),
                    SettingsDocumentPoint("Google Gemini", "When an AI request requires external processing, the relevant prompt and conversation context may be sent to Google Gemini to generate a response."),
                    SettingsDocumentPoint("Infrastructure providers", "Database, hosting, email, network, and security providers may process information necessary to keep Mongosky operational."),
                ),
            ),
            SettingsDocumentBlock.Illustration("privacy-control-illustration", "privacy-control", "You should remain in control", "Review what you share, protect your account, and use Mongosky settings and support options to manage your information and privacy choices."),
            SettingsDocumentBlock.Section(
                id = "browser-storage", title = "6. Browser Storage and Login Sessions",
                paragraphs = listOf("Mongosky uses browser storage, including session storage and local storage, to support login sessions, temporary account-recovery steps, feature preferences, and selected application states.", "Optional third-party services may also use cookies or similar technologies under their own privacy policies.", "Removing browser data or signing out may end your session or reset locally stored preferences."),
            ),
            SettingsDocumentBlock.Section(
                id = "data-security", title = "7. How We Protect Information",
                paragraphs = listOf("Mongosky applies reasonable technical and organizational measures designed to protect personal information from unauthorized access, alteration, disclosure, misuse, or loss.", "Passwords created directly with Mongosky are protected using one-way password hashing and are not returned through the public account API. Authenticated requests use access tokens to identify the signed-in user.", "No website, transmission method, or storage system can guarantee absolute security. You should use a strong password, protect your device, and never share passwords or verification codes."),
            ),
            SettingsDocumentBlock.Section(
                id = "data-retention", title = "8. How Long We Keep Information",
                paragraphs = listOf("Mongosky keeps information for as long as reasonably necessary to provide the platform, maintain security, resolve disputes, enforce agreements, and meet legal obligations.", "When content or an account is deleted, some information may remain temporarily in backups, security records, or systems where retention is legally or technically necessary.", "Retention periods may vary depending on the type of information, the reason it is processed, and applicable legal requirements."),
            ),
            SettingsDocumentBlock.Section(
                id = "your-choices", title = "9. Your Choices and Privacy Rights",
                points = listOf(
                    SettingsDocumentPoint("Review your information", "You may review the information displayed on your profile and within available account settings."),
                    SettingsDocumentPoint("Correct your information", "You may update inaccurate or incomplete personal details through available profile or account controls."),
                    SettingsDocumentPoint("Remove content", "You may edit or delete eligible content using the controls provided for that content."),
                    SettingsDocumentPoint("Deactivate or delete your account", "You may use available account controls or Help and Support to request account deactivation or deletion."),
                    SettingsDocumentPoint("Withdraw optional access", "You may stop using optional features or revoke eligible third-party permissions through the relevant provider."),
                    SettingsDocumentPoint("Request assistance", "You may use Help and Support for questions about accessing, correcting, or deleting personal information."),
                ),
            ),
            SettingsDocumentBlock.Section(
                id = "public-information", title = "10. Information You Share Publicly",
                paragraphs = listOf("You are responsible for considering the audience before publishing information on Mongosky.", "Avoid sharing passwords, verification codes, financial information, identity documents, private addresses, medical information, precise live locations, or other sensitive personal information in public posts, comments, profiles, or messages.", "Other users may copy, download, capture, or redistribute content that you make visible to them. Mongosky cannot fully control information after another person has copied or shared it outside the platform."),
            ),
            SettingsDocumentBlock.Section(
                id = "children-privacy", title = "11. Children’s Privacy",
                paragraphs = listOf("Users must meet the minimum age and consent requirements that apply in their location.", "A child who cannot legally consent to the processing of personal information should use Mongosky only with valid permission and appropriate involvement from a parent or legal guardian.", "If you believe a child has provided personal information without the permission required by law, contact Mongosky through Help and Support."),
            ),
            SettingsDocumentBlock.Section(
                id = "international-processing", title = "12. International Data Processing",
                paragraphs = listOf("Mongosky and its service providers may process or store information in countries other than the country where you live.", "Where required, reasonable safeguards will be used to protect information transferred between countries."),
            ),
            SettingsDocumentBlock.Section(
                id = "policy-updates", title = "13. Updates to This Privacy Policy",
                paragraphs = listOf("Mongosky may update this Privacy Policy when platform features, data practices, service providers, or legal requirements change.", "The updated date displayed at the top of this page will show when the policy was most recently revised. Material changes may also be communicated through the platform when appropriate."),
            ),
            SettingsDocumentBlock.Section(
                id = "contact-us", title = "14. Contact Mongosky",
                paragraphs = listOf("If you have questions, concerns, or requests regarding this Privacy Policy or your personal information, contact Mongosky through the Help and Support option in Settings."),
            ),
        )
    )
}
