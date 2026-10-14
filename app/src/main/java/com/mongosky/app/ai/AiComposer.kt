package com.mongosky.app.ai

import com.mongosky.app.darkmode.AppColors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AiComposer(state:AiState,colors:AiColors,focus:FocusRequester,onDraft:(TextFieldValue,Long)->Unit,onSend:()->Unit) {
    val locked=!state.ready || state.expired || state.sending || state.messagesLoading || state.messagesRefreshing || state.mutating
    Row(Modifier.fillMaxWidth().heightIn(min=58.dp).background(AppColors.surface(colors.soft),RoundedCornerShape(27.dp))
        .border(1.dp,AppColors.outline(colors.border),RoundedCornerShape(27.dp)).padding(start=18.dp,end=5.dp,top=4.dp,bottom=4.dp),
        verticalAlignment=Alignment.Bottom) {
        BasicTextField(value=state.input,onValueChange={ incoming ->
            onDraft(incoming,state.inputRevision)
        },readOnly=locked,maxLines=6,textStyle=TextStyle(color=AppColors.content(colors.text),fontSize=16.sp,lineHeight=25.sp),
            cursorBrush=SolidColor(colors.text),keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences,imeAction=ImeAction.Send),
            keyboardActions=KeyboardActions(onSend={ if(state.canSend) onSend() }),
            modifier=Modifier.weight(1f).heightIn(min=48.dp,max=140.dp).focusRequester(focus).onPreviewKeyEvent { event ->
                if(event.type==KeyEventType.KeyDown && (event.key==Key.Enter || event.key==Key.NumPadEnter) && !event.isShiftPressed && state.input.composition==null) {
                    if(state.canSend) onSend()
                    true
                } else false
            },decorationBox={ inner ->
                Box(Modifier.padding(top=11.dp,bottom=11.dp)) {
                    if(state.draft.isEmpty()) Text("Message…",color=AppColors.content(colors.muted),fontSize=16.sp,lineHeight=25.sp)
                    inner()
                }
            })
        IconButton(onClick=onSend,enabled=state.canSend,modifier=Modifier.size(48.dp)) {
            if(state.sending) CircularProgressIndicator(Modifier.size(20.dp),color=AppColors.content(colors.muted),strokeWidth=2.dp)
            else Icon(AiIcons.Send,"Send message",tint=AppColors.content(if(state.canSend) colors.red else colors.muted),modifier=Modifier.size(21.dp))
        }
    }
}
