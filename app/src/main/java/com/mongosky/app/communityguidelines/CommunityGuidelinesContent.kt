package com.mongosky.app.communityguidelines

internal data class GuidelineRule(val title: String, val description: String)
internal data class GuidelineBlock(val id: String, val title: String, val description: String, val eyebrow: String = "", val paragraphs: List<String> = emptyList(), val rules: List<GuidelineRule> = emptyList(), val note: String? = null, val illustration: String? = null)
internal object CommunityGuidelinesContent {
    const val heroBadge = "A safer community starts with all of us"
    const val heroTitle = "Community Guidelines"
    const val heroDescription = "These guidelines explain the standards everyone must follow when using our platform. They are designed to protect freedom of expression while keeping the community safe, respectful, authentic, and welcoming."
    val heroHighlights = listOf("Respect every person", "Protect community safety", "Share authentic content", "Respect privacy and ownership")
    const val closingTitle = "Help us build a better community"
    const val closingDescription = "Pause before you post, consider how your actions may affect others, and use reporting tools when something does not feel safe. Respectful participation from every member makes the entire community stronger."
    const val closingPrimaryActionLabel = "Return to previous page"
    val blocks = listOf(
        GuidelineBlock(
            id = "our-shared-responsibility", title = "A community built on trust",
            description = "Our platform brings people together to share ideas, experiences, creativity, and meaningful conversations.",
            eyebrow = "Our shared responsibility",
            paragraphs = listOf("These guidelines apply to everyone and to all parts of the platform, including profiles, posts, comments, messages, images, videos, reels, links, usernames, and other shared content.", "We understand that context matters. When reviewing content, we may consider its purpose, language, cultural context, potential harm, public interest, and whether it was shared for awareness, education, documentation, or criticism.", "Using this platform means agreeing to follow these standards. Content or behaviour that violates them may be removed or restricted, and accounts may face further action depending on the seriousness or frequency of the violation."),
        ),
        GuidelineBlock(
            id = "respect-and-safety-card", title = "Every person deserves to feel safe",
            description = "Communicate with empathy, respect personal boundaries, and never use the platform to intimidate, exploit, or harm another person.",
            eyebrow = "Respect & Safety",
            illustration = "respect-and-safety",
        ),
        GuidelineBlock(
            id = "respect-every-person", title = "Treat everyone with dignity",
            description = "Different opinions are welcome, but attacks against people are not. Disagreement should focus on ideas and actions rather than humiliation or personal abuse.",
            eyebrow = "Respect",
            rules = listOf(GuidelineRule("Harassment and bullying", "Do not repeatedly insult, shame, threaten, mock, intimidate, or target another person. Encouraging others to participate in harassment is also prohibited."), GuidelineRule("Hate and discrimination", "Do not attack, degrade, exclude, or promote hatred against people based on race, ethnicity, nationality, religion, disability, age, sex, gender identity, sexual orientation, caste, or another protected characteristic."), GuidelineRule("Dehumanising language", "Do not describe individuals or groups as less than human, inherently dangerous, diseased, or deserving of violence or exclusion."), GuidelineRule("Unwanted sexual behaviour", "Sexual harassment, unwanted sexual comments, coercion, exploitation, and repeated sexual advances are not allowed.")),
        ),
        GuidelineBlock(
            id = "prevent-abuse", title = "Do not organise or encourage abuse",
            description = "The platform must not be used to direct harassment or coordinated attacks toward another person or community.",
            eyebrow = "Healthy conversations",
            rules = listOf(GuidelineRule("Targeted attacks", "Do not publish content with the purpose of sending abusive attention, threats, or humiliation toward a person."), GuidelineRule("Malicious exposure", "Do not share private or sensitive material to punish, shame, blackmail, or control someone."), GuidelineRule("False harmful accusations", "Do not knowingly publish fabricated allegations that could seriously damage another person's safety or reputation.")),
        ),
        GuidelineBlock(
            id = "protect-safety", title = "Protect life, safety, and wellbeing",
            description = "Content that creates a credible risk of physical harm may be removed immediately and, when necessary, referred to an appropriate authority.",
            eyebrow = "Safety",
            rules = listOf(GuidelineRule("Violence and threats", "Do not make credible threats, celebrate serious violence, request violent acts, or encourage others to physically harm a person or group."), GuidelineRule("Dangerous organisations", "Support, recruitment, praise, or operational assistance for terrorist, extremist, organised criminal, or violent groups is prohibited."), GuidelineRule("Dangerous activities", "Do not encourage challenges, instructions, or behaviour that could foreseeably cause serious injury, poisoning, or death."), GuidelineRule("Self-harm and suicide", "Do not encourage, glorify, or provide instructions for suicide or self-harm. Supportive discussion, recovery stories, and help-seeking information are allowed.")),
            note = "If you believe someone is in immediate danger, contact local emergency services instead of relying only on an in-platform report.",
        ),
        GuidelineBlock(
            id = "protect-young-people", title = "Children and young people require greater protection",
            description = "We apply strict standards to content or behaviour that could exploit, endanger, sexualise, manipulate, or inappropriately contact a minor.",
            eyebrow = "Youth protection",
            rules = listOf(GuidelineRule("Child exploitation", "Any content, request, conversation, or behaviour involving the sexual exploitation or abuse of a minor is strictly prohibited."), GuidelineRule("Grooming and coercion", "Adults must not build relationships with minors for sexual, exploitative, manipulative, or otherwise harmful purposes."), GuidelineRule("Unsafe exposure", "Do not reveal a minor's private location, school, contact details, routine, or other information that could place them at risk.")),
        ),
        GuidelineBlock(
            id = "authenticity-and-trust-card", title = "Be real, honest, and transparent",
            description = "Authentic participation helps people understand who and what they can trust. Never mislead others for influence, money, or artificial engagement.",
            eyebrow = "Authenticity & Trust",
            illustration = "authenticity-and-trust",
        ),
        GuidelineBlock(
            id = "be-authentic", title = "Represent yourself honestly",
            description = "You may use a preferred name, creative identity, parody, or fan account, but you must not intentionally deceive others about who you are.",
            eyebrow = "Authenticity",
            rules = listOf(GuidelineRule("Impersonation", "Do not pretend to be another person, organisation, public figure, or authorised representative in a way that is likely to mislead people."), GuidelineRule("Deceptive accounts", "Do not create accounts to hide coordinated abuse, avoid enforcement, manipulate conversations, or conduct fraudulent activity."), GuidelineRule("Artificial engagement", "Do not buy, sell, automate, or exchange followers, reactions, comments, views, or other engagement in a misleading way."), GuidelineRule("Undisclosed interests", "Clearly disclose paid promotions, sponsorships, affiliate relationships, and other material connections where appropriate.")),
        ),
        GuidelineBlock(
            id = "share-responsibly", title = "Share information responsibly",
            description = "People should be able to make decisions without being deliberately manipulated by false, fabricated, or materially altered information.",
            eyebrow = "Information integrity",
            rules = listOf(GuidelineRule("Harmful misinformation", "Do not knowingly spread false information that is likely to cause serious physical, financial, civic, or public harm."), GuidelineRule("Manipulated media", "Do not use edited, synthetic, or AI-generated media to deceive people about important events or make someone appear to say or do something they did not."), GuidelineRule("Missing context", "Do not present old, unrelated, or selectively edited material as evidence of a current event when doing so could seriously mislead people."), GuidelineRule("Constructive correction", "When possible, correct significant errors clearly. Satire, parody, opinion, and good-faith mistakes may be treated differently when their context is apparent.")),
        ),
        GuidelineBlock(
            id = "spam-scams", title = "Keep the community free from spam and scams",
            description = "Do not use the platform to manipulate users, distribute harmful material, or obtain money, accounts, or personal information through deception.",
            eyebrow = "Platform integrity",
            rules = listOf(GuidelineRule("Fraud and scams", "Fake investments, fraudulent sales, advance-fee schemes, impersonation scams, and deceptive fundraising are prohibited."), GuidelineRule("Phishing and malicious links", "Do not request passwords or verification codes, distribute malware, or direct users to deceptive websites designed to steal information."), GuidelineRule("Repetitive spam", "Do not repeatedly publish unwanted promotions, duplicate comments, irrelevant links, or mass messages that disrupt normal use."), GuidelineRule("Fake promotions", "Giveaways, job offers, discounts, and competitions must not use false claims, hidden charges, or misleading participation requirements.")),
        ),
        GuidelineBlock(
            id = "ownership", title = "Respect intellectual property",
            description = "Share only content you created or have permission and a lawful basis to use.",
            eyebrow = "Creative rights",
            rules = listOf(GuidelineRule("Copyright", "Do not upload or redistribute protected images, music, videos, writing, software, or other work without permission or a valid legal exception."), GuidelineRule("Trademarks", "Do not misuse names, logos, or brand identities in a way that causes confusion about source, ownership, or endorsement."), GuidelineRule("Creator attribution", "Credit does not automatically replace permission. Follow the creator's licence and usage conditions whenever you reuse content.")),
        ),
        GuidelineBlock(
            id = "privacy-and-reporting-card", title = "Protect privacy and speak up safely",
            description = "Control what you share, respect the privacy of others, and report content or behaviour that may place someone at risk.",
            eyebrow = "Privacy & Reporting",
            illustration = "privacy-and-reporting",
        ),
        GuidelineBlock(
            id = "protect-privacy", title = "Protect personal and confidential information",
            description = "Everyone has a responsibility to consider how shared information could affect another person's privacy, security, and safety.",
            eyebrow = "Privacy",
            rules = listOf(GuidelineRule("Private information", "Do not expose home addresses, private phone numbers, financial information, identity documents, passwords, medical records, or precise live locations without permission."), GuidelineRule("Doxxing", "Do not collect or publish identifying information to intimidate, harass, blackmail, or place someone at risk."), GuidelineRule("Intimate privacy", "Never share, threaten to share, or request intimate images or recordings without the clear consent of every person involved."), GuidelineRule("Images of others", "Consider consent, context, and safety before sharing identifiable images or recordings of another person, especially a child or vulnerable individual.")),
        ),
        GuidelineBlock(
            id = "reporting", title = "Report concerns responsibly",
            description = "Reporting helps us identify content and behaviour that may violate these guidelines. Reports should be made honestly and with enough context for a fair review.",
            eyebrow = "Reporting",
            rules = listOf(GuidelineRule("Report the specific content", "Report the relevant post, comment, message, profile, or media item whenever possible so the review team can understand the concern."), GuidelineRule("Do not misuse reporting", "Do not submit false, coordinated, or retaliatory reports simply because you disagree with someone."), GuidelineRule("Protect yourself", "You may also block, mute, unfollow, restrict, or leave a conversation when those controls are available."), GuidelineRule("Preserve urgent evidence", "For serious threats, fraud, or safety concerns, preserve relevant information and contact an appropriate authority when necessary.")),
        ),
        GuidelineBlock(
            id = "enforcement", title = "How we respond to violations",
            description = "Our response depends on the nature, context, severity, and history of the violation, as well as the level of risk it creates.",
            eyebrow = "Enforcement",
            rules = listOf(GuidelineRule("Content action", "We may remove content, reduce its visibility, add a warning, restrict recommendations, or limit certain interactions."), GuidelineRule("Account action", "We may issue a warning, temporarily restrict features, suspend an account, or permanently disable access."), GuidelineRule("Immediate action", "Severe violations involving credible threats, child exploitation, serious fraud, or coordinated harm may result in immediate removal or account suspension."), GuidelineRule("Repeated violations", "Repeated attempts to violate the rules, avoid restrictions, or create replacement accounts may lead to stronger enforcement.")),
        ),
        GuidelineBlock(
            id = "appeals", title = "Appeals and mistakes",
            description = "Moderation decisions may sometimes be incomplete or incorrect. When an appeal option is available, users can request another review.",
            eyebrow = "Fair review",
            paragraphs = listOf("An appeal should explain why the content or account activity follows these guidelines or why important context may have been missed.", "A successful appeal may restore content, reverse an account restriction, or update the original decision. Repeated or abusive appeals may be limited."),
        ),
        GuidelineBlock(
            id = "updates", title = "These standards may evolve",
            description = "Online risks, technology, and community expectations change over time. We may update these guidelines to address new forms of harm and improve clarity.",
            eyebrow = "Keeping the guidelines current",
            paragraphs = listOf("Important changes should be communicated clearly. Continued use of the platform after an update means the latest version of these guidelines applies.", "These guidelines do not replace applicable laws, our Terms of Service, Privacy Policy, or additional rules that may apply to specific features."),
        ),
    )
    val navigation = blocks.filter { it.illustration == null }
}
