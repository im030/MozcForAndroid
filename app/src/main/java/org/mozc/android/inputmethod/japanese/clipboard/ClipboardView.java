package org.mozc.android.inputmethod.japanese.clipboard;

import org.mozc.android.inputmethod.japanese.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * Overlay which shows the internal clipboard history.
 */
public class ClipboardView extends LinearLayout {
  public interface OnEntryClickListener {
    void onEntryClick(ClipboardEntry entry);
  }

  public interface OnClearClickListener {
    void onClearClick();
  }

  public interface OnCloseClickListener {
    void onCloseClick();
  }

  private final ClipboardEntryAdapter adapter;
  private final ListView listView;
  private final TextView emptyView;

  private OnEntryClickListener onEntryClickListener;
  private OnClearClickListener onClearClickListener;
  private OnCloseClickListener onCloseClickListener;

  public ClipboardView(Context context) {
    this(context, null);
  }

  public ClipboardView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setOrientation(VERTICAL);
    setClickable(true);
    setFocusable(true);
    LayoutInflater.from(context).inflate(R.layout.clipboard_view, this, true);

    adapter = new ClipboardEntryAdapter(context);
    listView = ListView.class.cast(findViewById(R.id.clipboard_list));
    emptyView = TextView.class.cast(findViewById(R.id.clipboard_empty_view));
    listView.setAdapter(adapter);
    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (onEntryClickListener != null) {
          onEntryClickListener.onEntryClick(adapter.getItem(position));
        }
      }
    });

    View.class.cast(findViewById(R.id.clipboard_clear_button)).setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            if (onClearClickListener != null) {
              onClearClickListener.onClearClick();
            }
          }
        });
    ImageView closeButton = ImageView.class.cast(findViewById(R.id.clipboard_close_button));
    closeButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        if (onCloseClickListener != null) {
          onCloseClickListener.onCloseClick();
        } else {
          setVisibility(GONE);
        }
      }
    });
  }

  public void setOnEntryClickListener(OnEntryClickListener listener) {
    onEntryClickListener = listener;
  }

  public void setOnClearClickListener(OnClearClickListener listener) {
    onClearClickListener = listener;
  }

  public void setOnCloseClickListener(OnCloseClickListener listener) {
    onCloseClickListener = listener;
  }

  public void setEntries(List<ClipboardEntry> entries) {
    adapter.setEntries(entries);
    boolean empty = entries.isEmpty();
    listView.setVisibility(empty ? GONE : VISIBLE);
    emptyView.setVisibility(empty ? VISIBLE : GONE);
  }
}
