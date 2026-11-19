package org.mozc.android.inputmethod.japanese.ui;

import org.mozc.android.inputmethod.japanese.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InlineSuggestion;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.inline.InlineContentView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@TargetApi(Build.VERSION_CODES.R)
public class InlineSuggestionsView extends FrameLayout {

  private static final long INFLATE_DELAY_MS = 200;

  private final HorizontalScrollView scrollView;
  private final FrameLayout pinnedView;
  private final SurfaceView surfaceView;
  private final int itemMargin;
  private final List<SurfaceControl> pendingSurfaces = new ArrayList<>();
  private final List<Integer> originalWidths = new ArrayList<>();
  private LinearLayout content;
  private int generation;
  private int responseCount;
  private SurfaceControl parentSurfaceControl;

  public InlineSuggestionsView(Context context) {
    super(context);
    itemMargin = Math.round(4 * getResources().getDisplayMetrics().density);

    scrollView = new HorizontalScrollView(context);
    scrollView.setFillViewport(true);
    scrollView.setHorizontalScrollBarEnabled(false);

    pinnedView = new FrameLayout(context);
    pinnedView.setVisibility(View.GONE);

    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.addView(scrollView, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    row.addView(pinnedView, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(row, new LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    surfaceView = new SurfaceView(context);
    surfaceView.setZOrderOnTop(true);
    surfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
    surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
      @Override
      public void surfaceCreated(SurfaceHolder holder) {
        parentSurfaceControl = surfaceView.getSurfaceControl();
        for (SurfaceControl surfaceControl : new ArrayList<>(pendingSurfaces)) {
          reparent(surfaceControl);
        }
        reparentContent();
      }

      @Override
      public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      }

      @Override
      public void surfaceDestroyed(SurfaceHolder holder) {
        parentSurfaceControl = null;
      }
    });
    addView(surfaceView, new LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
  }

  public void clear() {
    generation++;
    scrollView.scrollTo(0, 0);
    if (content != null) {
      for (int i = 0; i < content.getChildCount(); i++) {
        View child = content.getChildAt(i);
        if (child instanceof InlineContentView) {
          reparent(((InlineContentView) child).getSurfaceControl(), null);
        }
      }
      content.removeAllViews();
      scrollView.removeView(content);
      content = null;
    }
    pinnedView.removeAllViews();
    pinnedView.setVisibility(View.GONE);
    pendingSurfaces.clear();
    originalWidths.clear();
  }

  public void setSuggestions(final List<InlineSuggestion> suggestions) {
    clear();
    responseCount = suggestions.size();
    if (suggestions.isEmpty()) {
      return;
    }
    final int currentGeneration = generation;
    postDelayed(new Runnable() {
      @Override
      public void run() {
        if (generation != currentGeneration) {
          return;
        }
        inflateSuggestions(suggestions, currentGeneration);
      }
    }, INFLATE_DELAY_MS);
  }

  public String getDebugInfo() {
    StringBuilder builder = new StringBuilder();
    int childCount = content != null ? content.getChildCount() : 0;
    builder.append("r=").append(responseCount)
        .append(" c=").append(childCount)
        .append(" cw=").append(content != null ? content.getWidth() : -1)
        .append(" vw=").append(getWidth())
        .append(" vh=").append(getHeight())
        .append(" host=").append(surfaceView.getWidth())
        .append('x').append(surfaceView.getHeight())
        .append(" sc=").append(parentSurfaceControl != null ? 1 : 0)
        .append(" pin=").append(pinnedView.getChildCount());
    for (int i = 0; i < childCount; i++) {
      View child = content.getChildAt(i);
      builder.append('\n').append(i)
          .append(':').append(child.getWidth())
          .append('x').append(child.getHeight())
          .append('@').append(child.getLeft())
          .append(" o=").append(i < originalWidths.size() ? originalWidths.get(i) : -999);
    }
    return builder.toString();
  }

  private void inflateSuggestions(List<InlineSuggestion> suggestions, int currentGeneration) {
    final InlineContentView[] views = new InlineContentView[suggestions.size()];
    final int[] remaining = {suggestions.size()};
    final Context context = getContext();
    final Size size = new Size(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        getResources().getDimensionPixelSize(R.dimen.button_frame_height));
    for (int i = 0; i < suggestions.size(); i++) {
      final int index = i;
      suggestions.get(i).inflate(context, size, context.getMainExecutor(),
          new Consumer<InlineContentView>() {
            @Override
            public void accept(InlineContentView view) {
              if (generation != currentGeneration) {
                return;
              }
              views[index] = view;
              remaining[0]--;
              if (remaining[0] == 0) {
                rebuild(suggestions, views);
              }
            }
          });
    }
  }

  private void rebuild(List<InlineSuggestion> suggestions, InlineContentView[] views) {
    InlineContentView pinned = null;
    List<InlineContentView> scrollable = new ArrayList<>(views.length);
    int extraPinnedCount = 0;
    for (int i = 0; i < views.length; i++) {
      InlineContentView view = views[i];
      if (view == null) {
        continue;
      }
      if (suggestions.get(i).getInfo().isPinned()) {
        if (pinned == null) {
          pinned = view;
        } else {
          scrollable.add(extraPinnedCount++, view);
        }
      } else {
        scrollable.add(view);
      }
    }

    if (pinned != null) {
      FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
          getIntrinsicWidth(pinned), ViewGroup.LayoutParams.MATCH_PARENT);
      layoutParams.setMarginStart(itemMargin);
      layoutParams.setMarginEnd(itemMargin);
      pinnedView.addView(pinned, layoutParams);
      pinnedView.setVisibility(View.VISIBLE);
    }

    content = new LinearLayout(getContext());
    content.setOrientation(LinearLayout.HORIZONTAL);
    content.setGravity(Gravity.CENTER_VERTICAL);
    for (final InlineContentView view : scrollable) {
      view.setSurfaceControlCallback(new InlineContentView.SurfaceControlCallback() {
        @Override
        public void onCreated(SurfaceControl surfaceControl) {
          reparent(surfaceControl);
        }

        @Override
        public void onDestroyed(SurfaceControl surfaceControl) {
          pendingSurfaces.remove(surfaceControl);
        }
      });
      int width = getIntrinsicWidth(view);
      originalWidths.add(width);
      LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
          width, ViewGroup.LayoutParams.MATCH_PARENT);
      layoutParams.setMarginStart(itemMargin);
      layoutParams.setMarginEnd(itemMargin);
      content.addView(view, layoutParams);
    }

    if (content.getChildCount() > 0) {
      scrollView.addView(content, new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
  }

  private static int getIntrinsicWidth(InlineContentView view) {
    ViewGroup.LayoutParams params = view.getLayoutParams();
    if (params != null && params.width > 0) {
      return params.width;
    }
    return ViewGroup.LayoutParams.WRAP_CONTENT;
  }

  private void reparentContent() {
    if (content == null) {
      return;
    }
    for (int i = 0; i < content.getChildCount(); i++) {
      View child = content.getChildAt(i);
      if (child instanceof InlineContentView) {
        reparent(((InlineContentView) child).getSurfaceControl());
      }
    }
  }

  private void reparent(SurfaceControl childSurfaceControl) {
    if (childSurfaceControl == null) {
      return;
    }
    SurfaceControl parent = parentSurfaceControl != null
        ? parentSurfaceControl
        : surfaceView.getSurfaceControl();
    if (parent == null) {
      if (!pendingSurfaces.contains(childSurfaceControl)) {
        pendingSurfaces.add(childSurfaceControl);
      }
      return;
    }
    parentSurfaceControl = parent;
    pendingSurfaces.remove(childSurfaceControl);
    reparent(childSurfaceControl, parent);
  }

  private static void reparent(
      SurfaceControl childSurfaceControl, SurfaceControl parentSurfaceControl) {
    if (childSurfaceControl == null) {
      return;
    }
    new SurfaceControl.Transaction()
        .reparent(childSurfaceControl, parentSurfaceControl)
        .apply();
  }
}
