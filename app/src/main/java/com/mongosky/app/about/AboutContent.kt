package com.mongosky.app.about

import com.mongosky.app.settingsinformation.*

// Exact published web copy. Keep policy text and dates in sync with the web source.
internal object AboutContent {
    val document = SettingsDocument(
        title = "About Mongosky",
        paragraphs = listOf("Mongosky is a social media platform created to help people connect, communicate, share their ideas, and discover meaningful content.", "The platform brings posts, images, videos, reels, comments, reactions, connections, messaging, and intelligent assistance together in one clean and accessible experience.", "Mongosky continues to grow with a focus on simplicity, performance, privacy, safety, and respectful communication."),
        blocks = listOf(
            SettingsDocumentBlock.Section(
                id = "what-is-mongosky", title = "1. What Is Mongosky?",
                paragraphs = listOf("Mongosky is a modern social communication platform where users can create profiles, publish content, interact with posts, watch reels, follow other users, and communicate with their community.", "The platform is designed to make sharing and communication feel simple on mobile, tablet, and desktop devices."),
            ),
            SettingsDocumentBlock.Illustration("connect-and-share-illustration", "connect-and-share", "Connect, communicate, and share", "Mongosky brings people and conversations together through posts, media, reactions, comments, connections, reels, and messaging."),
            SettingsDocumentBlock.Section(
                id = "platform-features", title = "2. What You Can Do on Mongosky",
                points = listOf(
                    SettingsDocumentPoint("Create and share posts", "Publish text, images, videos, captions, and other supported content with your community."),
                    SettingsDocumentPoint("Join conversations", "React to posts, write comments, reply to discussions, and participate in meaningful conversations."),
                    SettingsDocumentPoint("Watch reels", "Discover and watch short video content through the Mongosky Reels experience."),
                    SettingsDocumentPoint("Build connections", "Follow other users, view profiles, and stay connected with people and content that interest you."),
                    SettingsDocumentPoint("Use E-Text", "E-Text is Mongosky’s messaging feature for communication between Mongosky users."),
                    SettingsDocumentPoint("Use Mongosky AI", "Access intelligent assistance and platform information through the Mongosky AI experience."),
                ),
            ),
            SettingsDocumentBlock.Section(
                id = "our-purpose", title = "3. Our Purpose",
                paragraphs = listOf("Mongosky aims to create an online space where people can express themselves, communicate freely, discover useful content, and build genuine connections.", "The platform is being developed to remain easy to understand while supporting the performance, reliability, and security expected from a modern social media service."),
            ),
            SettingsDocumentBlock.Illustration("safe-community-illustration", "safe-community", "A respectful community belongs to everyone", "Mongosky encourages authentic expression, responsible sharing, personal privacy, respectful discussion, and the safety of every community member."),
            SettingsDocumentBlock.Section(
                id = "our-principles", title = "4. What Guides Us",
                points = listOf(
                    SettingsDocumentPoint("People first", "Features should be understandable, useful, accessible, and designed around real user needs."),
                    SettingsDocumentPoint("Respect and safety", "Every user should be able to participate without harassment, exploitation, deception, or avoidable harm."),
                    SettingsDocumentPoint("Privacy and control", "Users should understand how their information is handled and have practical ways to manage their account and content."),
                    SettingsDocumentPoint("Authentic communication", "Mongosky supports genuine identity, meaningful expression, and responsible interaction between users."),
                    SettingsDocumentPoint("Reliable performance", "The platform is developed with attention to speed, responsive design, stable navigation, and dependable data delivery."),
                    SettingsDocumentPoint("Continuous improvement", "Mongosky will continue to improve through careful development, testing, feedback, and responsible innovation."),
                ),
            ),
            SettingsDocumentBlock.Section(
                id = "our-story", title = "5. Our Story",
                paragraphs = listOf("Mongosky was launched on 20 November 2025 with the goal of building a distinctive and dependable social media platform.", "Since its launch, Mongosky has continued to expand its social features, media experience, community tools, account services, and technical foundation."),
            ),
            SettingsDocumentBlock.Section(
                id = "technology", title = "6. How Mongosky Is Being Built",
                paragraphs = listOf("Mongosky uses React and TypeScript for its frontend experience, Go for its backend services, and MongoDB for its database.", "The platform architecture is being developed to support responsive interfaces, maintainable features, efficient data access, media delivery, account security, and future growth."),
            ),
            SettingsDocumentBlock.Section(
                id = "leadership", title = "7. Leadership",
                points = listOf(
                    SettingsDocumentPoint("Founder", "Mongosky was founded by Murad Ahmed Simanto."),
                    SettingsDocumentPoint("Co-founder", "A.H. Rohan, also known as Arafat Hasan Rohan, is the co-founder and works on the management of the Mongosky website."),
                ),
            ),
            SettingsDocumentBlock.Illustration("future-growth-illustration", "future-growth", "Building the next Mongosky experience", "Mongosky is continuing to improve its website while developing smooth and consistent mobile experiences for Android and iOS."),
            SettingsDocumentBlock.Section(
                id = "whats-next", title = "8. What Comes Next",
                paragraphs = listOf("Mongosky’s Android and iOS applications are currently under development. The team is working on performance, navigation, media delivery, account security, notifications, privacy, and a consistent mobile experience.", "New features and improvements will be introduced carefully after development and testing so that the platform remains stable, useful, and easy to maintain."),
            ),
            SettingsDocumentBlock.Section(
                id = "community-responsibility", title = "9. Growing Together",
                paragraphs = listOf("A strong social platform depends on both responsible technology and responsible users.", "Everyone who uses Mongosky is encouraged to respect other people, protect private information, share authentic content, follow the Community Guidelines, and report harmful activity through the available support tools."),
            ),
            SettingsDocumentBlock.Section(
                id = "contact-mongosky", title = "10. Contact Mongosky",
                paragraphs = listOf("For questions, feedback, safety concerns, or assistance with Mongosky, use the Help and Support option available within Settings."),
            ),
        )
    )
}
