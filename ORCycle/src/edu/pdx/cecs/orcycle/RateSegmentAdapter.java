package edu.pdx.cecs.orcycle;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class RateSegmentAdapter extends ArrayAdapter<String> {
	private final Context context;
	private final String[] values;

	public RateSegmentAdapter(Context context, String[] values) {
		super(context, R.layout.rate_segment_list_item, values);
		this.context = context;
		this.values = values;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		View rowView = inflater.inflate(R.layout.rate_segment_list_item, parent, false);

		TextView textView = (TextView) rowView.findViewById(R.id.textViewRateSegmentListItem);

		textView.setText(values[position]);

		return rowView;
	}
}
