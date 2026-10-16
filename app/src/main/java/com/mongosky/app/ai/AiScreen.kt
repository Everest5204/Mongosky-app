package com.mongosky.app.ai

import com.mongosky.app.language.appText
import com.mongosky.app.language.appDescription

import com.mongosky.app.darkmode.AppColors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AiScreen(viewModel:AiViewModel,onSignInAgain:()->Unit) {
    AiKeyboardWindow()
    val state by viewModel.controller.state.collectAsState()
    val dark=com.mongosky.app.darkmode.AppAppearance.isDark
    val colors=if(dark) AiColors.Dark else AiColors.Light
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    val focus=remember { FocusRequester() }
    val keyboard=LocalSoftwareKeyboardController.current
    var clearConfirm by remember { mutableStateOf(false) }
    var followRequest by rememberSaveable { mutableIntStateOf(0) }
    val controller=viewModel.controller
    fun close() { scope.launch { drawer.close() } }
    BackHandler(drawer.isOpen) { close() }
    MaterialTheme(colorScheme=colors.scheme(dark)) {
        ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=drawer.isOpen,
            scrimColor=if(dark) Color.Black.copy(alpha=.62f) else Color(0xFF0F172A).copy(alpha=.38f),
            drawerContent={
                ModalDrawerSheet(modifier=Modifier.fillMaxHeight().width((LocalConfiguration.current.screenWidthDp*.88f).dp.coerceAtMost(370.dp)),
                    drawerShape=RectangleShape,drawerContainerColor=AppColors.surface(colors.surface),drawerContentColor=AppColors.content(colors.text),
                    drawerTonalElevation=0.dp,windowInsets=WindowInsets(0,0,0,0)) {
                    AiChatDrawer(state,colors,dark,onClose=::close,onNew={
                        controller.newChat();scope.launch { drawer.close();focus.requestFocus() }
                    },onTheme={com.mongosky.app.darkmode.AppAppearance.setDark(!dark)},onOpen={controller.openChat(it);close()},
                        onDelete=controller::deleteChat,onClear={clearConfirm=true},onReload=controller::retryRead)
                }
            },modifier=Modifier.fillMaxSize().background(AppColors.surface(colors.background))) {
            Column(Modifier.fillMaxSize().background(AppColors.surface(colors.background)).imePadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min=66.dp).padding(start=8.dp,end=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={keyboard?.hide();scope.launch { drawer.open() }},modifier=Modifier.size(48.dp)) {
                        Icon(AiIcons.Menu,appDescription("Open chat menu"),tint=AppColors.content(colors.text),modifier=Modifier.size(24.dp))
                    }
                    Text("Mongosky",color=AppColors.content(colors.text),fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=8.dp))
                }
                HorizontalDivider(color=AppColors.outline(colors.border))
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    AiMessageList(state,colors,followRequest,Modifier.fillMaxHeight().widthIn(max=880.dp))
                }
                Column(Modifier.widthIn(max=880.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(start=14.dp,end=14.dp,bottom=14.dp,top=8.dp)) {
                    state.error?.let { error -> AiErrorBanner(error,state.expired,colors,
                        onReload=controller::retryRead,onDismiss=controller::dismissError,onSignInAgain=onSignInAgain,
                        enabled=!state.sending && !state.mutating) }
                    AiComposer(state,colors,focus,controller::changeInput) { if(state.canSend) { followRequest++;controller.send() } }
                }
            }
        }
        if(clearConfirm) AlertDialog(onDismissRequest={clearConfirm=false},title={Text(appText("Clear all chats?"))},
            text={Text(appText("Delete all Mongosky AI chats? This cannot be undone."))},
            confirmButton={TextButton(onClick={clearConfirm=false;controller.clearChats()},enabled=!state.sending && !state.mutating) { Text(appText("Delete all"),color=AppColors.content(colors.red)) }},
            dismissButton={TextButton(onClick={clearConfirm=false}) { Text(appText("Cancel"),color=AppColors.content(colors.text)) }},containerColor=AppColors.surface(colors.surface))
    }
}

@Composable
private fun AiErrorBanner(error:AiError,expired:Boolean,colors:AiColors,onReload:()->Unit,onDismiss:()->Unit,onSignInAgain:()->Unit,enabled:Boolean) {
    Column(Modifier.fillMaxWidth().padding(bottom=10.dp).background(AppColors.surface(colors.errorBackground),RoundedCornerShape(14.dp))
        .border(1.dp,AppColors.outline(colors.errorBorder),RoundedCornerShape(14.dp))) {
        Row(Modifier.fillMaxWidth().padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(appText(error.message),color=AppColors.content(colors.errorText),fontSize=13.sp,lineHeight=20.sp,modifier=Modifier.weight(1f).padding(vertical=10.dp))
            if(!expired) IconButton(onDismiss,Modifier.size(48.dp)) { Icon(AiIcons.Close,appDescription("Dismiss error"),tint=AppColors.content(colors.errorText),modifier=Modifier.size(18.dp)) }
        }
        if(expired) TextButton(onSignInAgain,modifier=Modifier.padding(start=4.dp)) { Text(appText("Sign in again"),color=AppColors.content(colors.errorText)) }
        else if(error.action!=AiErrorAction.NONE) TextButton(onReload,enabled=enabled,modifier=Modifier.padding(start=4.dp)) {
            Text(appText(if(error.action==AiErrorAction.MESSAGES) "Reload chat" else "Reload chats"),color=AppColors.content(colors.errorText))
        }
    }
}
