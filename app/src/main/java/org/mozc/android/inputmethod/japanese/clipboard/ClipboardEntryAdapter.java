package org.mozc.android.inputmethod.japanese.clipboard;

import org.mozc.android.inputmethod.japanese.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ClipboardEntryAdapter extends BaseAdapter {
  private static class ViewHolder {
    TextView textView;
    TextView timestampView;
  }

  private final LayoutInflater inflater;
  private final java.text.DateFormat dateFormat;
  private List<ClipboardEntry> entries = Collections.emptyList();

  ClipboardEntryAdapter(Context context) {
    inflater = LayoutInflater.from(context);
    dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
  }

  void setEntries(List<ClipboardEntry> entries) {
    this.entries = new ArrayList<ClipboardEntry>(entries);
    notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return entries.size();
  }

  @Override
  public ClipboardEntry getItem(int position) {
    return entries.get(position);
  }

  @Override
  public long getItemId(int position) {
    return entries.get(position).getId();
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    View view = convertView;
    ViewHolder holder;
    if (view == null) {
      view = inflater.inflate(R.layout.clipboard_entry, parent, false);
      holder = new ViewHolder();
      holder.textView = TextView.class.cast(view.findViewById(R.id.clipboard_entry_text));
      holder.timestampView =
          TextView.class.cast(view.findViewById(R.id.clipboard_entry_timestamp));
      view.setTag(holder);
    } else {
      holder = ViewHolder.class.cast(view.getTag());
    }

    ClipboardEntry entry = getItem(position);
    holder.textView.setText(entry.getText().replace('\n', ' '));
    holder.timestampView.setText(dateFormat.format(entry.getTimestamp()));
    return view;
  }
}
