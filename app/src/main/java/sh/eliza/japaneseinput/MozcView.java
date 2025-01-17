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

package sh.eliza.japaneseinput;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService.Insets;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.EnumSet;
import javax.annotation.Nullable;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.CompositionMode;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output;
import sh.eliza.japaneseinput.CandidateViewManager.KeyboardCandidateViewHeightListener;
import sh.eliza.japaneseinput.FeedbackManager.FeedbackEvent;
import sh.eliza.japaneseinput.LayoutParamsAnimator.InterpolationListener;
import sh.eliza.japaneseinput.ViewManagerInterface.LayoutAdjustment;
import sh.eliza.japaneseinput.keyboard.BackgroundDrawableFactory;
import sh.eliza.japaneseinput.keyboard.KeyEventHandler;
import sh.eliza.japaneseinput.keyboard.KeyState.MetaState;
import sh.eliza.japaneseinput.keyboard.Keyboard;
import sh.eliza.japaneseinput.keyboard.KeyboardView;
import sh.eliza.japaneseinput.model.SymbolCandidateStorage;
import sh.eliza.japaneseinput.model.SymbolMajorCategory;
import sh.eliza.japaneseinput.ui.SideFrameStubProxy;
import sh.eliza.japaneseinput.view.MozcImageView;
import sh.eliza.japaneseinput.view.Skin;

/**
 * Root {@code View} of the MechaMozc. It is expected that instance methods are used after inflation
 * is done.
 */
public class MozcView extends FrameLayout implements MemoryManageable {

  private static class DimensionPixelSize {
    final int imeWindowPartialWidth;
    final int imeWindowRegionInsetThreshold;
    final int narrowFrameHeight;
    final int narrowImeWindowHeight;
    final int sideFrameWidth;
    final int buttonFrameHeight;

    public DimensionPixelSize(Resources resources) {
      imeWindowPartialWidth = resources.getDimensionPixelSize(R.dimen.ime_window_partial_width);
      imeWindowRegionInsetThreshold =
          resources.getDimensionPixelSize(R.dimen.ime_window_region_inset_threshold);
      narrowFrameHeight = getNarrowFrameHeight(resources);
      narrowImeWindowHeight =
          resources.getDimensionPixelSize(R.dimen.narrow_candidate_window_height)
              + narrowFrameHeight;
      sideFrameWidth = resources.getDimensionPixelSize(R.dimen.side_frame_width);
      buttonFrameHeight = resources.getDimensionPixelSize(R.dimen.button_frame_height);
    }
  }

  private static class HeightLinearInterpolationListener implements InterpolationListener {
    final int fromHeight;
    final int toHeight;

    public HeightLinearInterpolationListener(int fromHeight, int toHeight) {
      this.fromHeight = fromHeight;
      this.toHeight = toHeight;
    }

    @Override
    public ViewGroup.LayoutParams calculateAnimatedParams(
        float interpolation, ViewGroup.LayoutParams currentLayoutParams) {
      currentLayoutParams.height = fromHeight + (int) ((toHeight - fromHeight) * interpolation);
      return currentLayoutParams;
    }
  }

  // TODO(hidehiko): Refactor CandidateViewListener along with View structure refactoring.
  class InputFrameFoldButtonClickListener implements OnClickListener {
    private final View keyboardView;
    private final int originalHeight;
    private final long foldDuration;
    private final Interpolator foldKeyboardViewInterpolator;
    private final long expandDuration;
    private final Interpolator expandKeyboardViewInterpolator;
    private final LayoutParamsAnimator layoutParamsAnimator;

    InputFrameFoldButtonClickListener(
        View keyboardView,
        int originalHeight,
        long foldDuration,
        Interpolator foldKeyboardViewInterpolator,
        long expandDuration,
        Interpolator expandKeyboardViewInterpolator,
        LayoutParamsAnimator layoutParamsAnimator) {
      this.keyboardView = keyboardView;
      this.originalHeight = originalHeight;
      this.foldDuration = foldDuration;
      this.foldKeyboardViewInterpolator = foldKeyboardViewInterpolator;
      this.expandDuration = expandDuration;
      this.expandKeyboardViewInterpolator = expandKeyboardViewInterpolator;
      this.layoutParamsAnimator = layoutParamsAnimator;
    }

    @Override
    public void onClick(View v) {
      if (keyboardView.getHeight() == originalHeight) {
        if (viewEventListener != null) {
          viewEventListener.onFireFeedbackEvent(v, FeedbackEvent.INPUTVIEW_FOLD);
        }
        layoutParamsAnimator.startAnimation(
            keyboardView,
            new HeightLinearInterpolationListener(keyboardView.getHeight(), 0),
            foldKeyboardViewInterpolator,
            foldDuration,
            0);
        ((CompoundButton) v).setChecked(true);
      } else {
        if (viewEventListener != null) {
          viewEventListener.onFireFeedbackEvent(v, FeedbackEvent.INPUTVIEW_EXPAND);
        }
        layoutParamsAnimator.startAnimation(
            keyboardView,
            new HeightLinearInterpolationListener(keyboardView.getHeight(), originalHeight),
            expandKeyboardViewInterpolator,
            expandDuration,
            0);
        ((CompoundButton) v).setChecked(false);
      }
    }
  }

  /** Manages background view height. */
  private class SoftwareKeyboardHeightListener implements KeyboardCandidateViewHeightListener {
    @Override
    public void onExpanded() {
      if (!isNarrowMode()) {
        changeBottomBackgroundHeight(imeWindowHeight);
      }
    }

    @Override
    public void onCollapse() {
      if (!isNarrowMode() && getSymbolInputView().getVisibility() != VISIBLE) {
        resetBottomBackgroundHeight();
      }
    }
  }

  private final InOutAnimatedFrameLayout.VisibilityChangeListener onVisibilityChangeListener =
      new InOutAnimatedFrameLayout.VisibilityChangeListener() {
        @Override
        public void onVisibilityChange() {
          updateInputFrameHeight();
        }
      };

  private final DimensionPixelSize dimensionPixelSize = new DimensionPixelSize(getResources());
  private final SideFrameStubProxy leftFrameStubProxy = new SideFrameStubProxy();
  private final SideFrameStubProxy rightFrameStubProxy = new SideFrameStubProxy();

  private ViewEventListener viewEventListener;
  private boolean fullscreenMode = false;
  private boolean narrowMode = false;
  private boolean buttonFrameVisible = true;
  private Skin skin = Skin.getFallbackInstance();
  private LayoutAdjustment layoutAdjustment = LayoutAdjustment.FILL;
  private int inputFrameHeight = 0;
  private int imeWindowHeight = 0;
  private int symbolInputViewHeight = 0;
  private Animation symbolInputViewInAnimation;
  private Animation symbolInputViewOutAnimation;

  private SoftwareKeyboardHeightListener softwareKeyboardHeightListener =
      new SoftwareKeyboardHeightListener();

  private CandidateViewManager candidateViewManager;
  private boolean allowFloatingCandidateMode;

  public MozcView(Context context) {
    super(context);
  }

  public MozcView(Context context, AttributeSet attrSet) {
    super(context, attrSet);
  }

  private static Animation createAlphaAnimation(float fromAlpha, float toAlpha, long duration) {
    AlphaAnimation animation = new AlphaAnimation(fromAlpha, toAlpha);
    animation.setDuration(duration);
    return animation;
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    setKeyboardHeightRatio(100);

    leftFrameStubProxy.initialize(
        this, R.id.stub_left_frame, R.id.left_adjust_button, R.raw.adjust_arrow_left);
    rightFrameStubProxy.initialize(
        this, R.id.stub_right_frame, R.id.right_adjust_button, R.raw.adjust_arrow_right);

    candidateViewManager =
        new CandidateViewManager(
            getKeyboardCandidateView(),
            (FloatingCandidateView) findViewById(R.id.floating_candidate_view));
  }

  private InputFrameFoldButtonClickListener createFoldButtonListener(View view, int height) {
    Resources resources = getResources();
    int foldOvershootDurationRate =
        resources.getInteger(R.integer.input_frame_fold_overshoot_duration_rate);
    int foldOvershootRate = resources.getInteger(R.integer.input_frame_fold_overshoot_rate);
    int expandOvershootDurationRate =
        resources.getInteger(R.integer.input_frame_expand_overshoot_duration_rate);
    int expandOvershootRate = resources.getInteger(R.integer.input_frame_expand_overshoot_rate);

    return new InputFrameFoldButtonClickListener(
        view,
        height,
        resources.getInteger(R.integer.input_frame_fold_duration),
        SequentialInterpolator.newBuilder()
            .add(new DecelerateInterpolator(), foldOvershootDurationRate, -foldOvershootRate / 1e6f)
            .add(new AccelerateInterpolator(), 1e6f - foldOvershootDurationRate, 1)
            .build(),
        resources.getInteger(R.integer.input_frame_expand_duration),
        SequentialInterpolator.newBuilder()
            .add(
                new DecelerateInterpolator(),
                expandOvershootDurationRate,
                1 + expandOvershootRate / 1e6f)
            .add(new AccelerateDecelerateInterpolator(), 1e6f - expandOvershootDurationRate, 1)
            .build(),
        new LayoutParamsAnimator(new Handler(Looper.myLooper())));
  }

  public void setEventListener(
      final ViewEventListener viewEventListener,
      OnClickListener widenButtonClickListener,
      OnClickListener leftAdjustButtonClickListener,
      OnClickListener rightAdjustButtonClickListener,
      OnClickListener microphoneButtonClickListener) {
    Preconditions.checkNotNull(viewEventListener);
    Preconditions.checkNotNull(widenButtonClickListener);
    Preconditions.checkNotNull(leftAdjustButtonClickListener);
    Preconditions.checkNotNull(rightAdjustButtonClickListener);
    Preconditions.checkNotNull(microphoneButtonClickListener);

    checkInflated();

    this.viewEventListener = viewEventListener;

    // Propagate the given listener into the child views.
    // Set CandidateViewListener as well here, because it uses viewEventListener.
    candidateViewManager.setEventListener(viewEventListener, softwareKeyboardHeightListener);

    getSymbolInputView()
        .setEventListener(
            viewEventListener,
            /** Click handler of the close button. */
            new OnClickListener() {
              @Override
              public void onClick(View v) {
                if (viewEventListener != null) {
                  viewEventListener.onFireFeedbackEvent(v, FeedbackEvent.SYMBOL_INPUTVIEW_CLOSED);
                }
                hideSymbolInputView();
              }
            },
            microphoneButtonClickListener);

    getNarrowFrame().setEventListener(viewEventListener, widenButtonClickListener);
    leftFrameStubProxy.setButtonOnClickListener(leftAdjustButtonClickListener);
    rightFrameStubProxy.setButtonOnClickListener(rightAdjustButtonClickListener);
    getMicrophoneButton().setOnClickListener(microphoneButtonClickListener);
  }

  public void setKeyEventHandler(KeyEventHandler keyEventHandler) {
    checkInflated();

    // Propagate the given keyEventHandler to the child views.
    getKeyboardView().setKeyEventHandler(keyEventHandler);
    getSymbolInputView().setKeyEventHandler(keyEventHandler);
  }

  // TODO(hidehiko): Probably we'd like to remove this method when we decide to move MVC model.
  @Nullable
  public Keyboard getKeyboard() {
    checkInflated();
    return getKeyboardView().getKeyboard().orNull();
  }

  public void setKeyboard(Keyboard keyboard) {
    checkInflated();
    getKeyboardView().setKeyboard(keyboard);
    CompositionMode compositionMode = keyboard.getSpecification().getCompositionMode();
    getNarrowFrame().setHardwareCompositionButtonImage(compositionMode);
    candidateViewManager.setHardwareCompositionMode(compositionMode);
  }

  public void setEmojiEnabled(boolean unicodeEmojiEnabled) {
    checkInflated();
    getSymbolInputView().setEmojiEnabled(unicodeEmojiEnabled);
  }

  public void setPasswordField(boolean isPasswordField) {
    checkInflated();
    getSymbolInputView().setPasswordField(isPasswordField);
  }

  public void setEditorInfo(EditorInfo editorInfo) {
    checkInflated();
    getKeyboardView().setEditorInfo(editorInfo);
    candidateViewManager.setEditorInfo(editorInfo);
  }

  public void setFlickSensitivity(int flickSensitivity) {
    checkInflated();
    getKeyboardView().setFlickSensitivity(flickSensitivity);
  }

  public void setSymbolCandidateStorage(SymbolCandidateStorage symbolCandidateStorage) {
    checkInflated();
    getSymbolInputView().setSymbolCandidateStorage(symbolCandidateStorage);
  }

  public void setPopupEnabled(boolean popupEnabled) {
    checkInflated();
    getKeyboardView().setPopupEnabled(popupEnabled);
    getSymbolInputView().setPopupEnabled(popupEnabled);
  }

  public boolean isPopupEnabled() {
    checkInflated();
    return getKeyboardView().isPopupEnabled();
  }

  public void setSkin(Skin skin) {
    Preconditions.checkNotNull(skin);
    checkInflated();
    this.skin = skin;
    getKeyboardView().setSkin(skin);
    getSymbolInputView().setSkin(skin);
    candidateViewManager.setSkin(skin);
    getMicrophoneButton()
        .setBackgroundDrawable(
            BackgroundDrawableFactory.createPressableDrawable(
                new ColorDrawable(skin.buttonFrameButtonPressedColor), Optional.absent()));
    getMicrophoneButton().setSkin(skin);
    leftFrameStubProxy.setSkin(skin);
    rightFrameStubProxy.setSkin(skin);
    getButtonFrame()
        .setBackgroundDrawable(skin.buttonFrameBackgroundDrawable.getConstantState().newDrawable());
    getNarrowFrame().setSkin(skin);
    getKeyboardFrameSeparator()
        .setBackgroundDrawable(
            skin.keyboardFrameSeparatorBackgroundDrawable.getConstantState().newDrawable());
  }

  public Skin getSkin() {
    return skin;
  }

  /**
   * Checks whether the inflation is finished or not. If not, throws an IllegalStateException, or do
   * nothing otherwise. Exposed as a package private method for testing purpose.
   */
  private void checkInflated() {
    if (getChildCount() == 0) {
      throw new IllegalStateException("It is necessary to inflate mozc_view.xml");
    }
  }

  public void setCommand(Command outCommand) {
    checkInflated();
    candidateViewManager.update(outCommand);
    updateMetaStatesBasedOnOutput(outCommand.getOutput());
  }

  // Update COMPOSING metastate.
  private void updateMetaStatesBasedOnOutput(Output output) {
    Preconditions.checkNotNull(output);

    boolean hasPreedit = output.hasPreedit() && output.getPreedit().getSegmentCount() > 0;
    if (hasPreedit) {
      getKeyboardView().updateMetaStates(EnumSet.of(MetaState.COMPOSING), Collections.emptySet());
    } else {
      getKeyboardView().updateMetaStates(Collections.emptySet(), EnumSet.of(MetaState.COMPOSING));
    }
  }

  public void onStartInputView(EditorInfo editorInfo) {
    candidateViewManager.onStartInputView(editorInfo);
  }

  public void setCursorAnchorInfo(CursorAnchorInfo info) {
    candidateViewManager.setCursorAnchorInfo(info);
  }

  public void setCursorAnchorInfoEnabled(boolean enabled) {
    allowFloatingCandidateMode = enabled;
    candidateViewManager.setAllowFloatingMode(enabled);
  }

  public void reset() {
    checkInflated();

    // Reset keyboard frame and view.
    resetKeyboardFrameVisibility();
    resetKeyboardViewState();

    // Reset candidate view.
    candidateViewManager.reset();

    // Reset symbol input view visibility. Set Visibility directly (without animation).
    SymbolInputView symbolInputView = getSymbolInputView();
    symbolInputView.clearAnimation();
    symbolInputView.setVisibility(View.GONE);

    // Reset *all* metastates (and set NO_GLOBE as default value).
    // Expecting metastates will be set next initialization.
    getKeyboardView()
        .updateMetaStates(EnumSet.of(MetaState.NO_GLOBE), EnumSet.allOf(MetaState.class));

    resetFullscreenMode();
    setLayoutAdjustmentAndNarrowMode(layoutAdjustment, narrowMode);
    resetBottomBackgroundHeight();
    updateBackgroundColor();
  }

  public void resetKeyboardFrameVisibility() {
    checkInflated();

    if (narrowMode) {
      return;
    }

    SymbolInputView symbolInputView = getSymbolInputView();
    View keyboardFrame;
    int keyboardFrameHeight;
    if (symbolInputView.isInflated() && symbolInputView.getVisibility() == View.VISIBLE) {
      keyboardFrame = getNumberKeyboardFrame();
      keyboardFrameHeight = symbolInputView.getNumberKeyboardHeight();
    } else {
      keyboardFrame = getKeyboardFrame();
      keyboardFrameHeight = getInputFrameHeight();
    }

    keyboardFrame.setVisibility(View.VISIBLE);

    // The height may be changed so reset it here.
    ViewGroup.LayoutParams layoutParams = keyboardFrame.getLayoutParams();
    if (layoutParams.height != keyboardFrameHeight) {
      layoutParams.height = keyboardFrameHeight;
      keyboardFrame.setLayoutParams(layoutParams);

      // Also reset the state of the folding button, which is "conceptually" a part of
      // the keyboard.
      candidateViewManager.setInputFrameFoldButtonChecked(false);
    }
  }

  public void resetKeyboardViewState() {
    checkInflated();
    getKeyboardView().resetState();
  }

  public boolean showSymbolInputView(Optional<SymbolMajorCategory> category) {
    Preconditions.checkNotNull(category);
    checkInflated();

    SymbolInputView view = getSymbolInputView();
    if (view.getVisibility() == View.VISIBLE) {
      return false;
    }

    if (!view.isInflated()) {
      view.inflateSelf();
      CandidateView numberCandidateView =
          (CandidateView) view.findViewById(R.id.candidate_view_in_symbol_view);
      numberCandidateView.setInputFrameFoldButtonOnClickListener(
          createFoldButtonListener(getNumberKeyboardFrame(), view.getNumberKeyboardHeight()));
      candidateViewManager.setNumberCandidateView(numberCandidateView);
    }

    view.resetToMajorCategory(category);
    startSymbolInputViewInAnimation();
    candidateViewManager.setNumberMode(true);

    return true;
  }

  public boolean hideSymbolInputView() {
    checkInflated();

    SymbolInputView view = getSymbolInputView();
    if (view.getVisibility() != View.VISIBLE) {
      return false;
    }

    candidateViewManager.setNumberMode(false);
    startSymbolInputViewOutAnimation();
    return true;
  }

  private int getButtonFrameHeightIfVisible() {
    return buttonFrameVisible ? dimensionPixelSize.buttonFrameHeight : 0;
  }

  /** Decides input frame height in not fullscreen mode. */
  private int getVisibleViewHeight() {
    checkInflated();

    boolean isSymbolInputViewVisible = getSymbolInputView().getVisibility() == View.VISIBLE;
    // Means only software keyboard or narrow frame
    boolean isDefaultView =
        !candidateViewManager.isKeyboardCandidateViewVisible() && !isSymbolInputViewVisible;

    if (narrowMode) {
      if (isDefaultView) {
        return dimensionPixelSize.narrowFrameHeight;
      } else {
        return dimensionPixelSize.narrowImeWindowHeight;
      }
    } else {
      if (isDefaultView) {
        return getInputFrameHeight() + getButtonFrameHeightIfVisible();
      } else {
        if (isSymbolInputViewVisible) {
          return symbolInputViewHeight;
        } else {
          return imeWindowHeight;
        }
      }
    }
  }

  private void updateInputFrameHeight() {
    // input_frame's height depends on fullscreen mode, narrow mode and Candidate/Symbol views.
    if (fullscreenMode) {
      setLayoutHeight(getBottomFrame(), getVisibleViewHeight());
      setLayoutHeight(getKeyboardView(), getInputFrameHeight());
      setLayoutHeight(getKeyboardFrame(), LayoutParams.WRAP_CONTENT);
    } else {
      if (narrowMode) {
        setLayoutHeight(getBottomFrame(), dimensionPixelSize.narrowImeWindowHeight);
      } else {
        setLayoutHeight(getBottomFrame(), imeWindowHeight);
        setLayoutHeight(getKeyboardView(), getInputFrameHeight());
        setLayoutHeight(getKeyboardFrame(), LayoutParams.WRAP_CONTENT);
      }
    }
  }

  private int getSideAdjustedWidth() {
    return dimensionPixelSize.imeWindowPartialWidth + dimensionPixelSize.sideFrameWidth;
  }

  public void setFullscreenMode(boolean fullscreenMode) {
    this.fullscreenMode = fullscreenMode;
  }

  public boolean isFullscreenMode() {
    return fullscreenMode;
  }

  private void resetFullscreenMode() {
    if (fullscreenMode) {
      // In fullscreen mode, InputMethodService shows extract view which height is 0 and
      // weight is 0. So our MozcView height should be fixed.
      // If CandidateView or SymbolInputView appears, MozcView height is enlarged to fix them.
      getOverlayView().setVisibility(View.GONE);
      setLayoutHeight(getTextInputFrame(), LayoutParams.WRAP_CONTENT);
      candidateViewManager.setOnVisibilityChangeListener(Optional.of(onVisibilityChangeListener));
      getSymbolInputView().setOnVisibilityChangeListener(onVisibilityChangeListener);
    } else {
      getOverlayView().setVisibility(View.VISIBLE);
      setLayoutHeight(getTextInputFrame(), LayoutParams.MATCH_PARENT);
      candidateViewManager.setOnVisibilityChangeListener(Optional.absent());
      getSymbolInputView().setOnVisibilityChangeListener(null);
    }
    candidateViewManager.setExtractedMode(fullscreenMode);
    updateInputFrameHeight();
    updateBackgroundColor();
  }

  private static void setLayoutHeight(View view, int height) {
    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
    layoutParams.height = height;
    view.setLayoutParams(layoutParams);
  }

  public boolean isNarrowMode() {
    return narrowMode;
  }

  public boolean isFloatingCandidateMode() {
    return candidateViewManager.isFloatingMode();
  }

  public Rect getKeyboardSize() {
    Resources resources = getResources();
    // TODO(yoichio): replace resources.getDisplayMetrics().widthPixels with targetWindow.width.
    return new Rect(
        0,
        0,
        layoutAdjustment == LayoutAdjustment.FILL
            ? resources.getDisplayMetrics().widthPixels
            : dimensionPixelSize.imeWindowPartialWidth,
        getInputFrameHeight());
  }

  /**
   * Sets {@code LayoutAdjustment} and {@code narrowMode}.
   *
   * <p>They are highly dependent on one another so this method sets both at the same time. This
   * decision makes caller-side simpler.
   */
  public void setLayoutAdjustmentAndNarrowMode(
      LayoutAdjustment layoutAdjustment, boolean narrowMode) {
    checkInflated();

    this.layoutAdjustment = layoutAdjustment;
    this.narrowMode = narrowMode;

    // If on narrowMode, the view is always shown with full-width regard less of given
    // layoutAdjustment.
    LayoutAdjustment temporaryAdjustment = narrowMode ? LayoutAdjustment.FILL : layoutAdjustment;

    View view = getForegroundFrame();
    FrameLayout.LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
    Resources resources = getResources();
    layoutParams.width =
        temporaryAdjustment == LayoutAdjustment.FILL
            ? resources.getDisplayMetrics().widthPixels
            : getSideAdjustedWidth();
    layoutParams.gravity = Gravity.BOTTOM;
    if (temporaryAdjustment == LayoutAdjustment.LEFT) {
      layoutParams.gravity |= Gravity.LEFT;
    } else if (temporaryAdjustment == LayoutAdjustment.RIGHT) {
      layoutParams.gravity |= Gravity.RIGHT;
    }
    view.setLayoutParams(layoutParams);

    leftFrameStubProxy.setFrameVisibility(
        temporaryAdjustment == LayoutAdjustment.RIGHT ? VISIBLE : GONE);
    rightFrameStubProxy.setFrameVisibility(
        temporaryAdjustment == LayoutAdjustment.LEFT ? VISIBLE : GONE);

    // Set candidate and description text size.
    float candidateTextSize =
        layoutAdjustment == LayoutAdjustment.FILL
            ? resources.getDimension(R.dimen.candidate_text_size)
            : resources.getDimension(R.dimen.candidate_text_size_aligned_layout);
    float descriptionTextSize =
        layoutAdjustment == LayoutAdjustment.FILL
            ? resources.getDimension(R.dimen.candidate_description_text_size)
            : resources.getDimension(R.dimen.candidate_description_text_size_aligned_layout);
    candidateViewManager.setCandidateTextDimension(candidateTextSize, descriptionTextSize);
    getSymbolInputView().setCandidateTextDimension(candidateTextSize, descriptionTextSize);

    // In narrow mode, hide software keyboard and show narrow status bar.
    candidateViewManager.setNarrowMode(narrowMode);
    if (narrowMode) {
      getKeyboardFrame().setVisibility(GONE);
      getButtonFrame().setVisibility(GONE);
      getNarrowFrame().setVisibility(GONE);
    } else {
      getKeyboardFrame().setVisibility(VISIBLE);
      getButtonFrame().setVisibility(buttonFrameVisible ? VISIBLE : GONE);
      getNarrowFrame().setVisibility(GONE);
      resetKeyboardFrameVisibility();
    }

    updateInputFrameHeight();
    updateBackgroundColor();
  }

  public void startLayoutAdjustmentAnimation() {
    Resources resources = getResources();
    int delta =
        resources.getDisplayMetrics().widthPixels - dimensionPixelSize.imeWindowPartialWidth;
    TranslateAnimation translateAnimation =
        new TranslateAnimation(layoutAdjustment == LayoutAdjustment.LEFT ? delta : -delta, 0, 0, 0);
    translateAnimation.setDuration(
        resources.getInteger(R.integer.layout_adjustment_transition_duration));
    translateAnimation.setInterpolator(new DecelerateInterpolator());
    getForegroundFrame().startAnimation(translateAnimation);
  }

  private void updateBackgroundColor() {
    // If fullscreenMode, background should not show original window.
    // If narrowMode, it is always full-width.
    // If isFloatingMode, background should be transparent.
    int resourceId =
        (fullscreenMode || (!narrowMode && !isFloatingMode())) ? R.color.input_frame_background : 0;
    getBottomBackground().setBackgroundResource(resourceId);
  }

  private boolean isFloatingMode() {
    return layoutAdjustment != LayoutAdjustment.FILL
        && !narrowMode
        && getResources().getDisplayMetrics().widthPixels
            >= dimensionPixelSize.imeWindowRegionInsetThreshold;
  }

  /** This function is called to compute insets. */
  public void setInsets(int contentViewWidth, int contentViewHeight, Insets outInsets) {
    if (!isFloatingMode()) {
      outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT;
      outInsets.contentTopInsets = contentViewHeight - getVisibleViewHeight();
      outInsets.visibleTopInsets = outInsets.contentTopInsets;
      return;
    }
    int height = getVisibleViewHeight();
    int width = getSideAdjustedWidth();
    int left = layoutAdjustment == LayoutAdjustment.RIGHT ? (contentViewWidth - width) : 0;

    outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
    outInsets.touchableRegion.set(
        left, contentViewHeight - height, left + width, contentViewHeight);
    outInsets.contentTopInsets = contentViewHeight;
    outInsets.visibleTopInsets = contentViewHeight;
  }

  private void changeBottomBackgroundHeight(int targetHeight) {
    if (getBottomBackground().getHeight() != targetHeight) {
      setLayoutHeight(getBottomBackground(), targetHeight);
    }
  }

  private void resetBottomBackgroundHeight() {
    setLayoutHeight(getBottomBackground(), getInputFrameHeight() + getButtonFrameHeightIfVisible());
  }

  private void startSymbolInputViewInAnimation() {
    if (fullscreenMode) {
      // Disable the animation during fullscreen mode to avoid ugly UI.
      getSymbolInputView().setVisibility(VISIBLE);
    } else {
      getSymbolInputView().startInAnimation();
    }
    changeBottomBackgroundHeight(symbolInputViewHeight);
  }

  private void startSymbolInputViewOutAnimation() {
    if (fullscreenMode) {
      // Disable the animation during fullscreen mode to avoid ugly UI.
      getSymbolInputView().setVisibility(GONE);
    } else {
      getSymbolInputView().startOutAnimation();
    }
    if (!candidateViewManager.isKeyboardCandidateViewVisible()) {
      resetBottomBackgroundHeight();
    }
  }

  /**
   * Reset components depending inputFrameHeight or imeWindowHeight. This should be called when
   * inputFrameHeight and/or imeWindowHeight are updated.
   */
  private void resetHeightDependingComponents() {
    getKeyboardCandidateView()
        .setInputFrameFoldButtonOnClickListener(
            createFoldButtonListener(getKeyboardFrame(), getInputFrameHeight()));

    if (candidateViewManager != null) {
      candidateViewManager.resetHeightDependingComponents(
          getResources(), imeWindowHeight, inputFrameHeight);
    }

    SymbolInputView symbolInputView = getSymbolInputView();
    {
      long duration = getResources().getInteger(R.integer.symbol_input_transition_duration);
      float fromAlpha = 0.3f;
      float toAlpha = 1.0f;

      symbolInputViewInAnimation = createAlphaAnimation(fromAlpha, toAlpha, duration);
      symbolInputView.setInAnimation(symbolInputViewInAnimation);
      symbolInputViewOutAnimation = createAlphaAnimation(toAlpha, fromAlpha, duration);
      symbolInputView.setOutAnimation(symbolInputViewOutAnimation);
    }

    if (symbolInputView.isInflated()) {
      CandidateView numberCandidateView =
          (CandidateView) symbolInputView.findViewById(R.id.candidate_view_in_symbol_view);
      numberCandidateView.setInputFrameFoldButtonOnClickListener(
          createFoldButtonListener(
              getNumberKeyboardFrame(), symbolInputView.getNumberKeyboardHeight()));
    }

    // Reset side adjust buttons height.
    leftFrameStubProxy.resetAdjustButtonBottomMargin(getInputFrameHeight());
    rightFrameStubProxy.resetAdjustButtonBottomMargin(getInputFrameHeight());
  }

  /**
   * Sets keyboard height rated to original height.
   *
   * @param keyboardHeightRatio target ratio percentage. Default is 100.
   */
  public void setKeyboardHeightRatio(int keyboardHeightRatio) {
    checkInflated();

    Resources resources = getResources();
    float heightScale = keyboardHeightRatio * 0.01f;
    float originalImeWindowHeight = resources.getDimension(R.dimen.ime_window_height);
    float originalInputFrameHeight = resources.getDimension(R.dimen.input_frame_height);
    inputFrameHeight = Math.round(originalInputFrameHeight * heightScale);
    int minImeWindowHeight = inputFrameHeight + dimensionPixelSize.buttonFrameHeight;
    imeWindowHeight =
        Math.max(Math.round(originalImeWindowHeight * heightScale), minImeWindowHeight);
    symbolInputViewHeight = Math.min(imeWindowHeight, minImeWindowHeight);

    updateInputFrameHeight();
    getSymbolInputView().setVerticalDimension(symbolInputViewHeight, heightScale);
    resetHeightDependingComponents();
  }

  private int getInputFrameHeight() {
    return inputFrameHeight;
  }

  private View getKeyboardFrame() {
    return findViewById(R.id.keyboard_frame);
  }

  View getKeyboardFrameSeparator() {
    return findViewById(R.id.keyboard_frame_separator);
  }

  private View getNumberKeyboardFrame() {
    return findViewById(R.id.number_keyboard_frame);
  }

  private KeyboardView getKeyboardView() {
    return (KeyboardView) findViewById(R.id.keyboard_view);
  }

  private CandidateView getKeyboardCandidateView() {
    return (CandidateView) findViewById(R.id.candidate_view);
  }

  private SymbolInputView getSymbolInputView() {
    return (SymbolInputView) findViewById(R.id.symbol_input_view);
  }

  private View getOverlayView() {
    return findViewById(R.id.overlay_view);
  }

  private LinearLayout getTextInputFrame() {
    return (LinearLayout) findViewById(R.id.textinput_frame);
  }

  private NarrowFrameView getNarrowFrame() {
    return (NarrowFrameView) findViewById(R.id.narrow_frame);
  }

  private View getForegroundFrame() {
    return findViewById(R.id.foreground_frame);
  }

  private View getBottomFrame() {
    return findViewById(R.id.bottom_frame);
  }

  private View getBottomBackground() {
    return findViewById(R.id.bottom_background);
  }

  private View getButtonFrame() {
    return findViewById(R.id.button_frame);
  }

  private MozcImageView getMicrophoneButton() {
    return (MozcImageView) findViewById(R.id.microphone_button);
  }

  private static int getNarrowFrameHeight(Resources resources) {
    return 0;
  }

  @Override
  public void trimMemory() {
    getKeyboardView().trimMemory();
    getSymbolInputView().trimMemory();
    candidateViewManager.trimMemory();
  }

  void setGlobeButtonEnabled(boolean globeButtonEnabled) {
    getKeyboardView().setGlobeButtonEnabled(globeButtonEnabled);
  }

  void setMicrophoneButtonEnabled(boolean microphoneButtonEnabled) {
    boolean lastButtonFrameVisible = buttonFrameVisible;
    if (narrowMode) {
      buttonFrameVisible = false;
    } else {
      buttonFrameVisible = microphoneButtonEnabled;
      int visibility = buttonFrameVisible ? View.VISIBLE : View.GONE;
      getButtonFrame().setVisibility(visibility);
      getMicrophoneButton().setVisibility(visibility);
      getSymbolInputView().setMicrophoneButtonEnabled(microphoneButtonEnabled);
      resetBottomBackgroundHeight();
    }
    if (lastButtonFrameVisible != buttonFrameVisible) {
      updateInputFrameHeight();
    }
  }
}
