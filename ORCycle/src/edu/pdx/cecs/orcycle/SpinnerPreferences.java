package edu.pdx.cecs.orcycle;

import java.util.List;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.widget.Spinner;

public class SpinnerPreferences {

	private final SharedPreferences settings;
	private final Editor editor;

	public SpinnerPreferences(SharedPreferences settings) {
		this.settings = settings;
		this.editor = settings.edit();
	}

	/**
	 * Saves spinner selection to preferences editor
	 * @param editor
	 * @param spinner
	 * @param key
	 */
	public void save(Spinner spinner, int key) {
		editor.putInt("" + key, spinner.getSelectedItemPosition());
	}

	/**
	 * Saves MultiSelectionSpinner selections to preferences editor
	 * @param editor
	 * @param spinner
	 * @param key
	 */
	public void save(MultiSelectionSpinner spinner, int key) {
		editor.putString("" + key, spinner.getSelectedIndicesAsString());
	}

	public void save(MultiSelectionSpinner spinner, int key, int[] answers, int otherPrefIndex, int otherId) {

		editor.putString("" + key, spinner.getSelectedIndicesAsString());

		// If other is one of the selections, save the text inormation
		List<Integer> selectedIndicies = spinner.getSelectedIndicies();
		for (int index : selectedIndicies) {
			if ((otherId >= 0) && (answers[index] == otherId)) {
				editor.putString("" + otherPrefIndex, spinner.getOtherText());
			}
		}
	}

	public void commit() {
		editor.commit();
	}

	public void recall(Spinner spinner, int key) {
		spinner.setSelection(settings.getInt(String.valueOf(key), 0));
	}

	public void recall(Spinner spinner, String key) {
		spinner.setSelection(settings.getInt(key, 0));
	}

	public void recall(MultiSelectionSpinner spinner, int key) {
		spinner.setSelection(settings.getString(String.valueOf(key), ""));
	}

	public void recall(MultiSelectionSpinner spinner, String key) {
		spinner.setSelection(settings.getString(key, ""));
	}

	public void recall(MultiSelectionSpinner spinner, int key, int keyOther) {
		spinner.setSelection(settings.getString(String.valueOf(key), ""));
		spinner.setOtherText(settings.getString(String.valueOf(keyOther), ""));
	}

}
