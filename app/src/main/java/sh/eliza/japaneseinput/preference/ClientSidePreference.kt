// Copyright 2010-2018, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package sh.eliza.japaneseinput.preference

import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.ViewManagerInterface.LayoutAdjustment
import sh.eliza.japaneseinput.emoji.EmojiProviderType
import sh.eliza.japaneseinput.view.SkinType

/**
 * This class expresses the client-side preferences which corresponds to current device
 * configuration.
 */
class ClientSidePreference {
    /**
     * Keyboard layout. A user can choose one of the layouts, and then the keyboard (sub)layouts are
     * activated.
     *
     *
     * Here "(sub)layouts" means that for example QWERTY layout contains following (sub)layouts;
     * QWERTY for Hiragana, QWERTY for Alphabet, QWERTY Symbol for Hiragana, and QWERTY Symbol for
     * Alphabet. If a user choose a keyboard layout, (s)he can switch among the (sub)layouts which
     * belong to the selected layout.
     */
    enum class KeyboardLayout( // ID for usage stats.
      @JvmField val id: Int
    ) {
        TWELVE_KEYS(1), QWERTY(2), GODAN(3)

    }

    /** A user's input style.  */
    enum class InputStyle {
        TOGGLE, FLICK, TOGGLE_FLICK
    }

    /** Hardware Keyboard Mapping.  */
    enum class HardwareKeyMap {
        DEFAULT, JAPANESE109A
    }

    @JvmField
    val isHapticFeedbackEnabled: Boolean
    @JvmField
    val hapticFeedbackDuration: Long
    @JvmField
    val isSoundFeedbackEnabled: Boolean
    @JvmField
    val soundFeedbackVolume: Int
    @JvmField
    val isPopupFeedbackEnabled: Boolean
    @JvmField
    val keyboardLayout: KeyboardLayout?
    @JvmField
    val inputStyle: InputStyle?
    val isQwertyLayoutForAlphabet: Boolean
    val isFullscreenMode: Boolean
    @JvmField
    val flickSensitivity: Int
    @JvmField
    val emojiProviderType: EmojiProviderType?
    @JvmField
    val hardwareKeyMap: HardwareKeyMap?
    @JvmField
    val skinType: SkinType?
    @JvmField
    val isMicrophoneButtonEnabled: Boolean
    @JvmField
    val layoutAdjustment: LayoutAdjustment?

    /** Percentage of keyboard height  */
    @JvmField
    val keyboardHeightRatio: Int

    /**
     * If you want to use this method, consider using [.ClientSidePreference] instead.
     */
    @VisibleForTesting
    constructor(
        isHapticFeedbackEnabled: Boolean,
        hapticFeedbackDuration: Long,
        isSoundFeedbackEnabled: Boolean,
        soundFeedbackVolume: Int,
        isPopupFeedbackEnabled: Boolean,
        keyboardLayout: KeyboardLayout?,
        inputStyle: InputStyle?,
        qwertyLayoutForAlphabet: Boolean,
        fullscreenMode: Boolean,
        flickSensitivity: Int,
        emojiProviderType: EmojiProviderType?,
        hardwareKeyMap: HardwareKeyMap?,
        skinType: SkinType?,
        isMicrophoneButtonEnabled: Boolean,
        layoutAdjustment: LayoutAdjustment?,
        keyboardHeightRatio: Int
    ) {
        this.isHapticFeedbackEnabled = isHapticFeedbackEnabled
        this.hapticFeedbackDuration = hapticFeedbackDuration
        this.isSoundFeedbackEnabled = isSoundFeedbackEnabled
        this.soundFeedbackVolume = soundFeedbackVolume
        this.isPopupFeedbackEnabled = isPopupFeedbackEnabled
        this.keyboardLayout = Preconditions.checkNotNull(keyboardLayout)
        this.inputStyle = Preconditions.checkNotNull(inputStyle)
        isQwertyLayoutForAlphabet = qwertyLayoutForAlphabet
        isFullscreenMode = fullscreenMode
        this.flickSensitivity = flickSensitivity
        this.emojiProviderType = Preconditions.checkNotNull(emojiProviderType)
        this.hardwareKeyMap = Preconditions.checkNotNull(hardwareKeyMap)
        this.skinType = Preconditions.checkNotNull(skinType)
        this.isMicrophoneButtonEnabled = isMicrophoneButtonEnabled
        this.layoutAdjustment = Preconditions.checkNotNull(layoutAdjustment)
        this.keyboardHeightRatio = keyboardHeightRatio
    }

    constructor(
        sharedPreferences: SharedPreferences, resources: Resources, deviceOrientation: Int
    ) {
        Preconditions.checkNotNull(sharedPreferences)
        isHapticFeedbackEnabled =
            sharedPreferences.getBoolean(PreferenceUtil.PREF_HAPTIC_FEEDBACK_KEY, false)
        hapticFeedbackDuration =
            sharedPreferences.getInt(PreferenceUtil.PREF_HAPTIC_FEEDBACK_DURATION_KEY, 30).toLong()
        isSoundFeedbackEnabled =
            sharedPreferences.getBoolean(PreferenceUtil.PREF_SOUND_FEEDBACK_KEY, false)
        soundFeedbackVolume =
            sharedPreferences.getInt(PreferenceUtil.PREF_SOUND_FEEDBACK_VOLUME_KEY, 50)
        isPopupFeedbackEnabled =
            sharedPreferences.getBoolean(PreferenceUtil.PREF_POPUP_FEEDBACK_KEY, true)
        isMicrophoneButtonEnabled =
            sharedPreferences.getBoolean(PreferenceUtil.PREF_VOICE_INPUT_KEY, true)
        val keyboardLayoutKey: String
        val inputStyleKey: String
        val qwertyLayoutForAlphabetKey: String
        val flickSensitivityKey: String
        val layoutAdjustmentKey: String
        val keyboardHeightRatioKey: String
        if (PreferenceUtil.isLandscapeKeyboardSettingActive(sharedPreferences, deviceOrientation)) {
            keyboardLayoutKey = PreferenceUtil.PREF_LANDSCAPE_KEYBOARD_LAYOUT_KEY
            inputStyleKey = PreferenceUtil.PREF_LANDSCAPE_INPUT_STYLE_KEY
            qwertyLayoutForAlphabetKey =
                PreferenceUtil.PREF_LANDSCAPE_QWERTY_LAYOUT_FOR_ALPHABET_KEY
            flickSensitivityKey = PreferenceUtil.PREF_LANDSCAPE_FLICK_SENSITIVITY_KEY
            layoutAdjustmentKey = PreferenceUtil.PREF_LANDSCAPE_LAYOUT_ADJUSTMENT_KEY
            keyboardHeightRatioKey = PreferenceUtil.PREF_LANDSCAPE_KEYBOARD_HEIGHT_RATIO_KEY
        } else {
            keyboardLayoutKey = PreferenceUtil.PREF_PORTRAIT_KEYBOARD_LAYOUT_KEY
            inputStyleKey = PreferenceUtil.PREF_PORTRAIT_INPUT_STYLE_KEY
            qwertyLayoutForAlphabetKey = PreferenceUtil.PREF_PORTRAIT_QWERTY_LAYOUT_FOR_ALPHABET_KEY
            flickSensitivityKey = PreferenceUtil.PREF_PORTRAIT_FLICK_SENSITIVITY_KEY
            layoutAdjustmentKey = PreferenceUtil.PREF_PORTRAIT_LAYOUT_ADJUSTMENT_KEY
            keyboardHeightRatioKey = PreferenceUtil.PREF_PORTRAIT_KEYBOARD_HEIGHT_RATIO_KEY
        }

        // Don't apply pref_portrait_keyboard_settings_for_landscape for fullscreen mode.
        val fullscreenKey =
            if (deviceOrientation == Configuration.ORIENTATION_LANDSCAPE) PreferenceUtil.PREF_LANDSCAPE_FULLSCREEN_KEY else PreferenceUtil.PREF_PORTRAIT_FULLSCREEN_KEY
        keyboardLayout = PreferenceUtil.getEnum(
            sharedPreferences,
            keyboardLayoutKey,
            KeyboardLayout::class.java,
            KeyboardLayout.TWELVE_KEYS,
            KeyboardLayout.GODAN
        )
        inputStyle = PreferenceUtil.getEnum(
            sharedPreferences, inputStyleKey, InputStyle::class.java, InputStyle.TOGGLE_FLICK
        )
        isQwertyLayoutForAlphabet = sharedPreferences.getBoolean(qwertyLayoutForAlphabetKey, false)
        // On large screen device, pref_portrait_fullscreen_key and
        // pref_landscape_fullscreen_key are omitted
        // so below default value "false" is applied.
        isFullscreenMode = sharedPreferences.getBoolean(fullscreenKey, false)
        flickSensitivity = sharedPreferences.getInt(flickSensitivityKey, 0)
        emojiProviderType = PreferenceUtil.getEnum(
            sharedPreferences,
            PreferenceUtil.PREF_EMOJI_PROVIDER_TYPE,
            EmojiProviderType::class.java,
            EmojiProviderType.NONE
        )
        hardwareKeyMap = PreferenceUtil.getEnum(
            sharedPreferences,
            PreferenceUtil.PREF_HARDWARE_KEYMAP,
            HardwareKeyMap::class.java,
            HardwareKeyMap.DEFAULT
        )
        skinType = PreferenceUtil.getEnum(
            sharedPreferences,
            resources.getString(R.string.pref_skin_type_key),
            SkinType::class.java,
            SkinType.valueOf(resources.getString(R.string.pref_skin_type_default))
        )
        layoutAdjustment = PreferenceUtil.getEnum(
            sharedPreferences,
            layoutAdjustmentKey,
            LayoutAdjustment::class.java,
            LayoutAdjustment.FILL
        )
        keyboardHeightRatio = sharedPreferences.getInt(keyboardHeightRatioKey, 100)
    }
}