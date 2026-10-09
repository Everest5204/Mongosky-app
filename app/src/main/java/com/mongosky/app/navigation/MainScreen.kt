@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mongosky.app.navigation

import androidx.compose.runtime.CompositionLocalProvider
import com.mongosky.app.post.FeedAuthor
import com.mongosky.app.profile.LocalProfileNavigator
import com.mongosky.app.profile.LocalProfileViewerId
import com.mongosky.app.profile.ProfileOpenRequest
import com.mongosky.app.profile.UserProfileScreen
import com.mongosky.app.profile.UserProfileViewModel
import com.mongosky.app.profile.profileRoute
import com.mongosky.app.profile.profileRouteUserId
import com.mongosky.app.profile.profileUserId
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.mongosky.app.auth.TokenStore
import com.mongosky.app.home.HomeFeedScreen
import com.mongosky.app.home.HomeFeedViewModel
import com.mongosky.app.mediapost.CreateMediaPostScreen
import com.mongosky.app.mediapost.MediaPostType
import com.mongosky.app.mediapost.MediaPostUploadLimits
import com.mongosky.app.mediapost.MediaPostUploadState
import com.mongosky.app.mediapost.MediaPostViewModel
import com.mongosky.app.mediapost.PostMediaKind
import com.mongosky.app.profile.OwnProfileScreen
import com.mongosky.app.profile.OwnProfileViewModel
import com.mongosky.app.reels.ReelsScreen
import com.mongosky.app.reels.ReelsViewModel
import com.mongosky.app.reels.ReelsPreviewViewModel
import com.mongosky.app.shared.ProfileAvatar
import com.mongosky.app.shared.preloadProfileAvatar
import com.mongosky.app.friends.FriendsRoute
import com.mongosky.app.friends.FriendsViewModel
import com.mongosky.app.search.SearchRoute
import com.mongosky.app.search.SearchViewModel
import com.mongosky.app.textpost.CreateTextPostScreen
import com.mongosky.app.textpost.TextPostPublication
import com.mongosky.app.textpost.TextPostViewModel
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Signed-in navigation shell with the account profile supplied by MainActivity.
 * Home connects the separate text and media feeds and native post actions.
 */
@Composable
fun MainScreen(
    userName: String,
    signingOut: Boolean,
    error: String?,
    onSignOut: () -> Unit,
    userId: String? = null,
    profileImageUrl: String? = null,
    onRefreshProfile: () -> Unit = {}
) {
    val activity = checkNotNull(LocalContext.current.mainActivity())
    val sessionKey = userId?.takeIf { it.isNotBlank() } ?: userName
    val avatarPixels = with(LocalDensity.current) { 80.dp.roundToPx() }
    DisposableEffect(activity.applicationContext, profileImageUrl, avatarPixels) {
        val preload = preloadProfileAvatar(activity.applicationContext, profileImageUrl, avatarPixels)
        onDispose { preload?.dispose() }
    }
    val owner = checkNotNull(activity as? ViewModelStoreOwner)
    // Mongosky Friends integration v1
    val friendsTokenStore = remember(activity.applicationContext) { TokenStore(activity.applicationContext) }
    val friendsViewModel = remember(owner) {
        ViewModelProvider(owner, FriendsViewModel.Factory(friendsTokenStore::read))
            .get("MongoskyFriends", FriendsViewModel::class.java)
    }
    LaunchedEffect(friendsViewModel, sessionKey, signingOut) {
        if (signingOut) friendsViewModel.endSession() else friendsViewModel.startSession(sessionKey)
    }
    DisposableEffect(friendsViewModel, activity) {
        onDispose { if (!activity.isChangingConfigurations) friendsViewModel.endSession() }
    }
    // Mongosky Search integration v2
    val searchTokenStore = remember(activity.applicationContext) { TokenStore(activity.applicationContext) }
    val searchViewModel = remember(owner) {
        ViewModelProvider(owner, SearchViewModel.Factory(searchTokenStore::read))
            .get("MongoskySearch", SearchViewModel::class.java)
    }
    LaunchedEffect(searchViewModel, sessionKey, signingOut) {
        if (signingOut) searchViewModel.endSession() else searchViewModel.startSession(sessionKey)
    }
    val feedViewModel = remember(owner) {
        ViewModelProvider(owner, HomeFeedViewModel.Factory(TokenStore(activity.applicationContext)))
            .get("MongoskyHomeFeed", HomeFeedViewModel::class.java)
    }
    val mediaPostViewModel = remember(owner) {
        ViewModelProvider(owner, MediaPostViewModel.Factory(activity.applicationContext))
            .get("MongoskyMediaPost", MediaPostViewModel::class.java)
    }
    val upload by mediaPostViewModel.uploadState.collectAsState()
    val composer by mediaPostViewModel.composerState.collectAsState()
    val textPostViewModel = remember(owner) {
        ViewModelProvider(owner, TextPostViewModel.Factory(TokenStore(activity.applicationContext)))
            .get("MongoskyTextPost", TextPostViewModel::class.java)
    }
    val textPublicationFlow = remember(textPostViewModel) {
        textPostViewModel.uiState.map { it.publication }.distinctUntilChanged()
    }
    val textPublication by textPublicationFlow.collectAsState(initial = textPostViewModel.uiState.value.publication)
    val ownProfileViewModel = remember(owner) {
        ViewModelProvider(owner, OwnProfileViewModel.Factory(activity.applicationContext))
            .get("MongoskyOwnProfile", OwnProfileViewModel::class.java)
    }
    val userProfileViewModel = remember(owner) {
        ViewModelProvider(owner, UserProfileViewModel.Factory(TokenStore(activity.applicationContext), feedViewModel::beginProfileCountRead))
            .get("MongoskyUserProfile", UserProfileViewModel::class.java)
    }
    val reelsViewModel = remember(owner) {
        ViewModelProvider(owner, ReelsViewModel.Factory(TokenStore(activity.applicationContext)))
            .get("MongoskyReels", ReelsViewModel::class.java)
    }
    val reelsPreviewViewModel = remember(owner) {
        ViewModelProvider(owner, ReelsPreviewViewModel.Factory(TokenStore(activity.applicationContext)))
            .get("MongoskyReelsPreview", ReelsPreviewViewModel::class.java)
    }
    LaunchedEffect(reelsViewModel, userId, signingOut) {
        if (!signingOut) { reelsViewModel.enter(userId); reelsPreviewViewModel.enter(userId) }
    }
    LaunchedEffect(ownProfileViewModel, userId, signingOut) {
        if (!signingOut) ownProfileViewModel.changes.collect { change ->
            if (change != null && change.ownerId.equals(userId, ignoreCase = true)) {
                if (change.mediaChanged) {
                    onRefreshProfile()
                    if (feedViewModel.uiState.sessionReady && !feedViewModel.uiState.sessionExpired) feedViewModel.refresh()
                }
                ownProfileViewModel.acknowledgeChange(change.version)
            }
        }
    }
    LaunchedEffect(textPostViewModel, userId, signingOut) {
        if (!signingOut) textPostViewModel.startSession(userId)
    }
    val feedState = feedViewModel.uiState
    LaunchedEffect(feedViewModel, mediaPostViewModel, sessionKey, signingOut) {
        if (!signingOut) {
            feedViewModel.startSession()
            mediaPostViewModel.startSession()
        }
    }
    DisposableEffect(feedViewModel, mediaPostViewModel, ownProfileViewModel, searchViewModel, activity) {
        onDispose {
            if (!activity.isChangingConfigurations) {
                searchViewModel.endSession()
                ownProfileViewModel.endSession()
                userProfileViewModel.endSession()
                reelsViewModel.endSession()
                reelsPreviewViewModel.endSession()
                textPostViewModel.endSession()
                mediaPostViewModel.endSession()
                feedViewModel.endSession()
            }
        }
    }
    var backStack by rememberSaveable(sessionKey, stateSaver = MainRouteStackSaver) {
        mutableStateOf(listOf(MainRoute.HOME.name))
    }
    var homeTopRequest by rememberSaveable(sessionKey) { mutableStateOf(0) }
    var createMenuOpen by remember { mutableStateOf(false) }
    var messagesNoticeOpen by remember { mutableStateOf(false) }
    var mediaPickerOpen by rememberSaveable(sessionKey) { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val contentState = rememberSaveableStateHolder()
    var profileHint by remember(sessionKey) { mutableStateOf<FeedAuthor?>(null) }
    val entry = backStack.lastOrNull() ?: MainRoute.HOME.name
    val route = MainRoute.values().firstOrNull { it.name == entry.substringBefore(':') } ?: MainRoute.HOME
    val isPostComposer = route == MainRoute.MEDIA_POST || route == MainRoute.TEXT_POST
    val drawerVisible = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open
    val pickerContract = remember { PickMultipleVisualMedia(MediaPostUploadLimits.MAX_IMAGES) }
    val mediaPicker = rememberLauncherForActivityResult(pickerContract) { uris ->
        val requested = mediaPickerOpen
        mediaPickerOpen = false
        if (requested && !signingOut && uris.isNotEmpty() &&
            upload === MediaPostUploadState.Idle && !composer.isResetting
        ) {
            mediaPostViewModel.startSession()
            mediaPostViewModel.discardComposer()
            mediaPostViewModel.selectMedia(uris)
            feedViewModel.closeOverlays()
            createMenuOpen = false
            if (backStack.lastOrNull() != MainRoute.MEDIA_POST.name) {
                backStack = backStack + MainRoute.MEDIA_POST.name
            }
        }
    }

    val success = upload as? MediaPostUploadState.Succeeded
    LaunchedEffect(success?.draft?.id, feedState.sessionReady, feedState.sessionExpired, signingOut) {
        val result = success ?: return@LaunchedEffect
        if (signingOut || !feedState.sessionReady || feedState.sessionExpired) return@LaunchedEffect
        ownProfileViewModel.recordPublishedMedia(userId, result.post)
        if (result.post.type == MediaPostType.VIDEO) {
            reelsViewModel.recordPublished(userId, result.post)
            reelsPreviewViewModel.recordPublished(userId, result.post)
            mediaPostViewModel.acknowledgeSuccess(result.draft.id)
            scope.launch { snackbar.showSnackbar("Video published.") }
        } else if (feedViewModel.insertPublishedPost(result.post)) {
            mediaPostViewModel.acknowledgeSuccess(result.draft.id)
        }
    }

    fun navigate(destination: MainRoute) {
        if (signingOut || mediaPickerOpen) return
        if (destination == MainRoute.MEDIA_POST || destination == MainRoute.CREATE_REEL) {
            createMenuOpen = false
            if (upload !== MediaPostUploadState.Idle || composer.isResetting) {
                backStack = listOf(MainRoute.HOME.name)
                homeTopRequest++
                scope.launch { snackbar.showSnackbar("Finish or dismiss your previous upload first.") }
                return
            }
            mediaPickerOpen = true
            onRefreshProfile()
            try {
                mediaPicker.launch(PickVisualMediaRequest(
                    if (destination == MainRoute.CREATE_REEL) PickVisualMedia.VideoOnly else PickVisualMedia.ImageAndVideo
                ))
            } catch (_: ActivityNotFoundException) {
                mediaPickerOpen = false
                scope.launch { snackbar.showSnackbar("No media picker is available on this phone.") }
            } catch (_: SecurityException) {
                mediaPickerOpen = false
                scope.launch { snackbar.showSnackbar("Couldn't open your gallery. Please try again.") }
            }
            return
        }
        if (destination == MainRoute.HOME && route == MainRoute.HOME) homeTopRequest++
        if (destination == MainRoute.TEXT_POST) onRefreshProfile()
        if (destination != MainRoute.HOME) feedViewModel.closeOverlays()
        createMenuOpen = false
        backStack = when {
            destination == MainRoute.HOME -> listOf(MainRoute.HOME.name)
            destination.isPrimaryRoute -> listOf(MainRoute.HOME.name, destination.name)
            destination == route -> backStack
            else -> backStack + destination.name
        }
    }

    fun openUserProfile(request: ProfileOpenRequest) {
        if (signingOut || mediaPickerOpen) return
        val target = profileUserId(request.userId) ?: return
        feedViewModel.closeOverlays()
        ownProfileViewModel.connections.close()
        userProfileViewModel.connections.close()
        reelsViewModel.comments.closeComments()
        createMenuOpen = false
        val destination = if (target == profileUserId(userId)) MainRoute.PROFILE.name else profileRoute(target)
        if (backStack.lastOrNull() == destination) return
        profileHint = request.author?.takeIf { profileUserId(it.id) == target }
        backStack = backStack + destination
        scope.launch { drawerState.close() }
    }

    fun navigateFromDrawer(destination: MainRoute) {
        navigate(destination)
        scope.launch { drawerState.close() }
    }

    fun signOut() {
        if (signingOut) return
        friendsViewModel.endSession()
        searchViewModel.endSession()
        ownProfileViewModel.endSession()
        userProfileViewModel.endSession()
        reelsViewModel.setForeground(false)
        reelsViewModel.endSession()
        reelsPreviewViewModel.endSession()
        textPostViewModel.endSession()
        mediaPostViewModel.endSession()
        mediaPickerOpen = false
        navigate(MainRoute.PROFILE)
        scope.launch { drawerState.close() }
        onSignOut()
    }

    fun editFailedUpload() {
        if (!signingOut && mediaPostViewModel.restoreFailedDraft()) {
            feedViewModel.closeOverlays()
            createMenuOpen = false
            backStack = backStack + MainRoute.MEDIA_POST.name
        }
    }

    fun checkUploadedPost() {
        val failed = upload as? MediaPostUploadState.Failed ?: return
        if (failed.draft.media.firstOrNull()?.kind == PostMediaKind.VIDEO) {
            navigate(MainRoute.REELS)
            reelsViewModel.refresh()
        } else {
            navigate(MainRoute.HOME)
            homeTopRequest++
            feedViewModel.refresh()
        }
    }

    LightMainSystemBars(dark = route == MainRoute.REELS)

    val publishedText = textPublication as? TextPostPublication.Published
    LaunchedEffect(publishedText?.post?.id, signingOut) {
        if (publishedText != null && !signingOut) {
            backStack = listOf(MainRoute.HOME.name)
            homeTopRequest++
            if (!feedState.sessionReady && !feedState.sessionExpired) feedViewModel.startSession()
        }
    }
    LaunchedEffect(publishedText?.post?.id, feedState.sessionReady, feedState.sessionExpired, signingOut) {
        val result = publishedText ?: return@LaunchedEffect
        if (!signingOut && feedState.sessionReady && !feedState.sessionExpired) {
            ownProfileViewModel.recordPublishedText(userId, result.post)
            if (feedViewModel.insertPublishedTextPost(result.post)) {
                textPostViewModel.acknowledgePublished(result.post.id)
            }
        }
    }

    CompositionLocalProvider(LocalProfileNavigator provides ::openUserProfile, LocalProfileViewerId provides userId) {
    MaterialTheme(colorScheme = MainPalette.colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = if (route == MainRoute.REELS) Color.Black else Color.White) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = !isPostComposer && drawerVisible &&
                        !signingOut && !mediaPickerOpen && !createMenuOpen,
                    scrimColor = Color.Black.copy(alpha = 0.42f),
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(320.dp).fillMaxHeight(),
                            drawerShape = RoundedCornerShape(0.dp),
                            drawerContainerColor = Color.White,
                            drawerTonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            MainDrawerContent(
                                userName = userName,
                                selectedRoute = route,
                                profileImageUrl = profileImageUrl,
                                enabled = !signingOut,
                                onClose = { scope.launch { drawerState.close() } },
                                onNavigate = ::navigateFromDrawer,
                                onSignOut = ::signOut
                            )
                        }
                    }
                ) {
                    BackHandler(
                        enabled = !isPostComposer && !mediaPickerOpen &&
                            !createMenuOpen && !messagesNoticeOpen &&
                            (signingOut || drawerVisible || backStack.size > 1)
                    ) {
                        when {
                            signingOut -> Unit
                            drawerVisible -> scope.launch { drawerState.close() }
                            else -> backStack = backStack.dropLast(1)
                        }
                    }

                    Scaffold(
                        containerColor = Color.White,
                        contentColor = MainPalette.text,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbar) },
                        topBar = friendsTopBar@ {
                            if (route == MainRoute.FRIENDS) return@friendsTopBar
                            if (!isPostComposer && route != MainRoute.SEARCH && route != MainRoute.PROFILE && route != MainRoute.USER_PROFILE && route != MainRoute.REELS) MainTopBar(
                                enabled = !signingOut && !mediaPickerOpen,
                                onHome = { navigate(MainRoute.HOME) },
                                onSearch = { navigate(MainRoute.SEARCH) },
                                onNotifications = { navigate(MainRoute.NOTIFICATIONS) },
                                onMessages = { messagesNoticeOpen = true },
                                onMenu = {
                                    createMenuOpen = false
                                    scope.launch { drawerState.open() }
                                }
                            )
                        },
                        bottomBar = {
                            if (!isPostComposer && route != MainRoute.SEARCH && route != MainRoute.REELS) MainBottomBar(
                                selectedRoute = route,
                                enabled = !signingOut && !mediaPickerOpen,
                                createMenuOpen = createMenuOpen,
                                onNavigate = ::navigate,
                                onToggleCreate = { createMenuOpen = !createMenuOpen },
                                onDismissCreate = { createMenuOpen = false }
                            )
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .padding(padding)
                                .consumeWindowInsets(padding)
                        ) {
                            contentState.SaveableStateProvider(entry) {
                                when (route) {
                                    MainRoute.HOME -> HomeFeedScreen(
                                        viewModel = feedViewModel,
                                        userName = userName,
                                        onSignInAgain = ::signOut,
                                        mediaPostViewModel = mediaPostViewModel,
                                        onEditUpload = ::editFailedUpload,
                                        onCheckUpload = ::checkUploadedPost,
                                        reelsPreviewViewModel = reelsPreviewViewModel,
                                        onOpenReel = { reelsViewModel.openReel(it); navigate(MainRoute.REELS) },
                                        onOpenReels = { navigate(MainRoute.REELS) },
                                        scrollToTopRequest = homeTopRequest
                                    )
                                    MainRoute.REELS -> ReelsScreen(
                                        viewModel = reelsViewModel, userId = userId, userName = userName,
                                        onBack = { backStack = backStack.dropLast(1).ifEmpty { listOf(MainRoute.HOME.name) } },
                                        onOwnProfile = { navigate(MainRoute.PROFILE) }, onSignInAgain = ::signOut
                                    )
                                    MainRoute.MEDIA_POST -> CreateMediaPostScreen(
                                        viewModel = mediaPostViewModel,
                                        userName = userName,
                                        profileImageUrl = profileImageUrl,
                                        onClose = {
                                            backStack = backStack.dropLast(1)
                                                .ifEmpty { listOf(MainRoute.HOME.name) }
                                        },
                                        onSubmitted = {
                                            navigate(MainRoute.HOME)
                                            homeTopRequest++
                                        }
                                    )
                                    MainRoute.TEXT_POST -> CreateTextPostScreen(
                                        viewModel = textPostViewModel,
                                        userName = userName,
                                        profileImageUrl = profileImageUrl,
                                        onClose = {
                                            backStack = backStack.dropLast(1)
                                                .ifEmpty { listOf(MainRoute.HOME.name) }
                                        },
                                        onCheckFeed = {
                                            navigate(MainRoute.HOME)
                                            homeTopRequest++
                                            feedViewModel.refresh()
                                        },
                                        onSignInAgain = ::signOut
                                    )
                                    MainRoute.SEARCH -> SearchRoute(
                                        viewModel = searchViewModel,
                                        onSignInAgain = ::signOut,
                                        onOpenProfile = { user ->
                                            openUserProfile(ProfileOpenRequest(
                                                user.id,
                                                FeedAuthor(user.id, user.firstName, user.lastName, user.imageUrl)
                                            ))
                                        }
                                    )
                                    MainRoute.FRIENDS -> FriendsRoute(
                                        viewModel = friendsViewModel,
                                        onBack = { if (!signingOut) backStack = backStack.dropLast(1).ifEmpty { listOf(MainRoute.HOME.name) } },
                                        onSignInAgain = ::signOut,
                                        onOpenProfile = { user ->
                                            openUserProfile(ProfileOpenRequest(
                                                user.id,
                                                FeedAuthor(user.id, user.firstName, user.lastName, user.imageUrl)
                                            ))
                                        }
                                    )
                                    MainRoute.USER_PROFILE -> UserProfileScreen(
                                        viewModel = userProfileViewModel, actionsModel = feedViewModel, viewerId = userId,
                                        userId = profileRouteUserId(entry).orEmpty(), viewerName = userName, hint = profileHint,
                                        onBack = { backStack = backStack.dropLast(1).ifEmpty { listOf(MainRoute.HOME.name) } },
                                        onSignInAgain = ::signOut
                                    )
                                    MainRoute.PROFILE -> OwnProfileScreen(
                                        viewModel = ownProfileViewModel,
                                        actionsModel = feedViewModel,
                                        userId = userId,
                                        userName = userName,
                                        signingOut = signingOut,
                                        signOutError = error,
                                        profileImageUrl = profileImageUrl,
                                        onSignOut = ::signOut,
                                        onBack = {
                                            if (!signingOut) backStack = backStack.dropLast(1)
                                                .ifEmpty { listOf(MainRoute.HOME.name) }
                                        }
                                    )
                                    else -> Text(
                                        text = route.title,
                                        modifier = Modifier.padding(24.dp),
                                        color = MainPalette.text,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (messagesNoticeOpen) {
            AlertDialog(
                onDismissRequest = { messagesNoticeOpen = false },
                containerColor = Color.White,
                title = { Text("Messages") },
                text = { Text("Messaging is currently unavailable.") },
                confirmButton = {
                    TextButton(onClick = { messagesNoticeOpen = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
    }
}

@Composable
private fun MainTopBar(
    enabled: Boolean,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onMessages: () -> Unit,
    onMenu: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f).height(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled, onClick = onHome),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Mongosky",
                    color = MainPalette.brand,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MainActionIcon(MainIcons.search, "Search people", enabled, onSearch)
            MainActionIcon(MainIcons.bell, "Notifications", enabled, onNotifications)
            MainActionIcon(MainIcons.message, "Messages", enabled, onMessages)
            MainActionIcon(MainIcons.menu, "Open menu", enabled, onMenu)
        }
        MainDivider()
    }
}

@Composable
private fun MainActionIcon(
    image: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = image,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MainPalette.text else MainPalette.muted
        )
    }
}

@Composable
private fun MainBottomBar(
    selectedRoute: MainRoute,
    enabled: Boolean,
    createMenuOpen: Boolean,
    onNavigate: (MainRoute) -> Unit,
    onToggleCreate: () -> Unit,
    onDismissCreate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        MainDivider()
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp)
                .padding(horizontal = 8.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainBottomItem(
                "Home", MainIcons.home, selectedRoute == MainRoute.HOME,
                enabled, { onNavigate(MainRoute.HOME) }, Modifier.weight(1f)
            )
            MainBottomItem(
                "Reels", MainIcons.reels, selectedRoute == MainRoute.REELS,
                enabled, { onNavigate(MainRoute.REELS) }, Modifier.weight(1f)
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(50.dp)) {
                    IconButton(
                        onClick = onToggleCreate,
                        enabled = enabled,
                        modifier = Modifier.size(50.dp).clip(CircleShape)
                            .background(MainPalette.soft)
                            .semantics {
                                stateDescription = if (createMenuOpen) "Expanded" else "Collapsed"
                            }
                    ) {
                        Icon(
                            imageVector = MainIcons.plus,
                            contentDescription = if (createMenuOpen) "Close create menu" else "Open create menu",
                            modifier = Modifier.size(28.dp),
                            tint = if (createMenuOpen || selectedRoute.isCreateRoute) {
                                MainPalette.brand
                            } else MainPalette.text
                        )
                    }
                    DropdownMenu(
                        expanded = createMenuOpen,
                        onDismissRequest = onDismissCreate,
                        modifier = Modifier.width(196.dp),
                        offset = DpOffset((-73).dp, (-8).dp),
                        shape = RoundedCornerShape(14.dp),
                        containerColor = Color.White,
                        tonalElevation = 0.dp,
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.dp, MainPalette.border)
                    ) {
                        MainCreateItem("Text post", MainIcons.edit) { onNavigate(MainRoute.TEXT_POST) }
                        MainCreateItem("Media post", MainIcons.image) { onNavigate(MainRoute.MEDIA_POST) }
                        MainCreateItem("Reels", MainIcons.reels) { onNavigate(MainRoute.CREATE_REEL) }
                        MainCreateItem("Story", MainIcons.story) { onNavigate(MainRoute.STORY) }
                        MainCreateItem("AI", MainIcons.zap) { onNavigate(MainRoute.AI) }
                    }
                }
            }
            MainBottomItem(
                "Friends", MainIcons.friends, selectedRoute == MainRoute.FRIENDS,
                enabled, { onNavigate(MainRoute.FRIENDS) }, Modifier.weight(1f)
            )
            MainBottomItem(
                "Profile", MainIcons.user, selectedRoute == MainRoute.PROFILE,
                enabled, { onNavigate(MainRoute.PROFILE) }, Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MainBottomItem(
    label: String,
    image: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = image,
                contentDescription = null,
                modifier = Modifier.size(if (label == "Reels") 29.dp else 27.dp),
                tint = if (selected && enabled) MainPalette.brand else MainPalette.muted
            )
        }
    }
}

@Composable
private fun MainCreateItem(label: String, image: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = MainPalette.text, fontSize = 16.sp) },
        leadingIcon = {
            Icon(image, contentDescription = null, tint = MainPalette.muted, modifier = Modifier.size(20.dp))
        },
        onClick = onClick
    )
}

@Composable
private fun MainDrawerContent(
    userName: String,
    selectedRoute: MainRoute,
    enabled: Boolean,
    profileImageUrl: String?,
    onClose: () -> Unit,
    onNavigate: (MainRoute) -> Unit,
    onSignOut: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onNavigate(MainRoute.PROFILE) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MainAvatar(userName, Modifier.size(48.dp), profileImageUrl)
                Text(
                    text = userName.ifBlank { "Mongosky" },
                    color = MainPalette.text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            MainActionIcon(MainIcons.close, "Close menu", true, onClose)
        }
        MainDivider()
        Spacer(Modifier.height(8.dp))

        MainDrawerItem(MainRoute.PROFILE, MainIcons.user, selectedRoute, enabled, onNavigate)
        MainDrawerItem(MainRoute.MY_ACTIVITY, MainIcons.history, selectedRoute, enabled, onNavigate)
        MainDrawerItem(MainRoute.SAVED_ITEMS, MainIcons.bookmark, selectedRoute, enabled, onNavigate)
        MainDrawerItem(MainRoute.SETTINGS, MainIcons.settings, selectedRoute, enabled, onNavigate)
        MainDrawerItem(MainRoute.GUIDELINES, MainIcons.shield, selectedRoute, enabled, onNavigate)
        MainDrawerItem(MainRoute.CHANGE_NAME, MainIcons.edit, selectedRoute, enabled, onNavigate)

        Spacer(Modifier.height(8.dp))
        MainDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onSignOut)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Icon(MainIcons.signOut, contentDescription = null, tint = MainPalette.brand, modifier = Modifier.size(24.dp))
            Text("Sign out", color = MainPalette.brand, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MainDrawerItem(
    route: MainRoute,
    image: ImageVector,
    selectedRoute: MainRoute,
    enabled: Boolean,
    onNavigate: (MainRoute) -> Unit
) {
    val selected = route == selectedRoute
    val tint = if (selected) MainPalette.brand else MainPalette.text
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MainPalette.soft else Color.White)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab) { onNavigate(route) }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Icon(image, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(route.title, color = tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MainAvatar(userName: String, modifier: Modifier, profileImageUrl: String?) {
    val initial = remember(userName) {
        val name = userName.trim()
        if (name.isEmpty()) "M" else String(Character.toChars(name.codePointAt(0))).uppercase(Locale.ROOT)
    }
    ProfileAvatar(imageUrl = profileImageUrl, modifier = modifier, fallbackText = initial)
}

@Composable
private fun MainDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MainPalette.border))
}

@Suppress("DEPRECATION")
@Composable
private fun LightMainSystemBars(dark: Boolean = false) {
    val activity = LocalContext.current.mainActivity()
    DisposableEffect(activity, dark) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val oldStatusLight = controller.isAppearanceLightStatusBars
            val oldNavigationLight = controller.isAppearanceLightNavigationBars
            val oldStatusColor = window.statusBarColor
            val oldNavigationColor = window.navigationBarColor
            window.statusBarColor = if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            window.navigationBarColor = if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            onDispose {
                controller.isAppearanceLightStatusBars = oldStatusLight
                controller.isAppearanceLightNavigationBars = oldNavigationLight
                window.statusBarColor = oldStatusColor
                window.navigationBarColor = oldNavigationColor
            }
        }
    }
}

private fun Context.mainActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        val base = context.baseContext
        if (base === context) break
        context = base
    }
    return context as? Activity
}

private enum class MainRoute(val title: String) {
    HOME("Home"), REELS("Reels"), FRIENDS("Friends"), PROFILE("Profile"), USER_PROFILE("Profile"),
    SEARCH("Search"), NOTIFICATIONS("Notifications"), TEXT_POST("Text post"),
    MEDIA_POST("Media post"), CREATE_REEL("Reels"), STORY("Story"), AI("AI"),
    MY_ACTIVITY("My Activity"), SAVED_ITEMS("Saved Items"), SETTINGS("Settings"),
    GUIDELINES("Community Guidelines"), CHANGE_NAME("Change name");

    val isCreateRoute: Boolean
        get() = this == TEXT_POST || this == MEDIA_POST || this == CREATE_REEL || this == STORY || this == AI

    val isPrimaryRoute: Boolean
        get() = this == HOME || this == REELS || this == FRIENDS || this == PROFILE
}

private val MainRouteStackSaver = listSaver<List<String>, String>(
    save = { it },
    restore = { it.ifEmpty { listOf(MainRoute.HOME.name) } }
)

private object MainPalette {
    val brand = Color(0xFFE60023)
    val text = Color(0xFF111827)
    val muted = Color(0xFF6B7280)
    val soft = Color(0xFFF3F4F6)
    val border = Color(0xFFEEF0F3)
    val colors = lightColorScheme(
        primary = brand, onPrimary = Color.White,
        secondary = Color(0xFF2563EB),
        background = Color.White, onBackground = text,
        surface = Color.White, onSurface = text,
        surfaceVariant = soft, onSurfaceVariant = muted,
        outline = border, error = brand
    )
}

/** Cached, local vector icons; no icon downloads or extra icon dependency. */
private object MainIcons {
    val home by lazy { outlineIcon("Home", "M3 10L12 3L21 10V20A1 1 0 0 1 20 21H15V14H9V21H4A1 1 0 0 1 3 20Z") }
    val search by lazy { outlineIcon("Search", "M18 10.5A7.5 7.5 0 1 1 3 10.5A7.5 7.5 0 1 1 18 10.5M16 16L21 21") }
    val bell by lazy { outlineIcon("Notifications", "M18 8A6 6 0 0 0 6 8C6 15 3 15 3 17H21C21 15 18 15 18 8M10 21A2 2 0 0 0 14 21") }
    val message by lazy { outlineIcon("Messages", "M21 11.5A9 9 0 0 1 7.1 19.1L3 21L4.9 16.9A9 9 0 1 1 21 11.5Z") }
    val menu by lazy { outlineIcon("Menu", "M3 6H21M3 12H21M3 18H21") }
    val user by lazy { outlineIcon("Profile", "M16 7A4 4 0 1 1 8 7A4 4 0 1 1 16 7M4 21V19A6 6 0 0 1 10 13H14A6 6 0 0 1 20 19V21") }
    val friends by lazy { outlineIcon("Friends", "M12 7A4 4 0 1 1 4 7A4 4 0 1 1 12 7M2 21V19A6 6 0 0 1 8 13A6 6 0 0 1 14 19V21M16 3A4 4 0 0 1 16 11M17 14A6 6 0 0 1 22 20V21") }
    val plus by lazy { outlineIcon("Create", "M12 5V19M5 12H19") }
    val close by lazy { outlineIcon("Close", "M6 6L18 18M18 6L6 18") }
    val edit by lazy { outlineIcon("Text post", "M15 5L19 9M4 20L5 15L17 3A2.8 2.8 0 0 1 21 7L9 19Z") }
    val image by lazy { outlineIcon("Media post", "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3ZM10 8A2 2 0 1 1 6 8A2 2 0 1 1 10 8M21 15L16 10L5 21") }
    val story by lazy { outlineIcon("Story", "M22 12A10 10 0 1 1 2 12A10 10 0 1 1 22 12M12 8V16M8 12H16") }
    val zap by lazy { outlineIcon("AI", "M13 2L3 14H11L10 22L21 10H13Z") }
    val history by lazy { outlineIcon("My Activity", "M3 11A9 9 0 1 1 5.5 18.5M3 4V11H10M12 7V12L16 14") }
    val bookmark by lazy { outlineIcon("Saved Items", "M5 3H19V21L12 17L5 21Z") }
    val settings by lazy { outlineIcon("Settings", "M10 3H14L15 6L18 5L21 9L19 12L21 15L18 19L15 18L14 21H10L9 18L6 19L3 15L5 12L3 9L6 5L9 6ZM15 12A3 3 0 1 1 9 12A3 3 0 1 1 15 12") }
    val shield by lazy { outlineIcon("Guidelines", "M12 3L20 6V12C20 17 16 20 12 22C8 20 4 17 4 12V6ZM8 12L11 15L16 9") }
    val signOut by lazy { outlineIcon("Sign out", "M10 3H4A1 1 0 0 0 3 4V20A1 1 0 0 0 4 21H10M8 12H21M17 8L21 12L17 16") }
    val reels by lazy {
        val builder = iconBuilder("Reels")
        builder.addPath(
            pathData = PathParser().parsePathString("M7.7 4.5H16.3A4.2 4.2 0 0 1 20.5 8.7V15.3A4.2 4.2 0 0 1 16.3 19.5H7.7A4.2 4.2 0 0 1 3.5 15.3V8.7A4.2 4.2 0 0 1 7.7 4.5ZM3.8 9H20.2M7.1 4.8L9.9 9M13.2 4.8L16 9").toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
        )
        builder.addPath(
            pathData = PathParser().parsePathString("M10 11.25V16.75L15 14Z").toNodes(),
            fill = SolidColor(Color.Black)
        )
        builder.build()
    }
}

private fun iconBuilder(name: String) = ImageVector.Builder(
    name = "Mongosky$name", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
)

private fun outlineIcon(name: String, data: String): ImageVector = iconBuilder(name).apply {
    addPath(
        pathData = PathParser().parsePathString(data).toNodes(),
        stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
    )
}.build()
