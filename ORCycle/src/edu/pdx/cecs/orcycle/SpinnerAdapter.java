package edu.pdx.cecs.orcycle;

import java.util.List;

import android.widget.Spinner;

public class SpinnerAdapter {

	private final long noteId;
	private final DbAdapter dbAdapter;

	public SpinnerAdapter(DbAdapter dbAdapter, long noteId) {
		this.dbAdapter = dbAdapter;
		this.noteId = noteId;
	}

	public void put(Spinner spinner, int question_id, int[] answer_ids) {
		submitSpinnerSelection(spinner, dbAdapter, question_id, answer_ids, -1, null);
	}
	/**
	 * Enters the spinner selection into the database
	 * @param spinner
	 * @param dbAdapter
	 * @param question_id
	 * @param answers
	 */
	public void submitSpinnerSelection(Spinner spinner, DbAdapter dbAdapter,
			int question_id, int[] answer_ids, int other_id, String other_text) {

		// Note: The first entry is always blank, the array of answers displayed
		// by the UI is one greater than the number of answers in the database.
		int answerIndex = spinner.getSelectedItemPosition() - 1;

		if (answerIndex >= 0) {
			if (answer_ids[answerIndex] == other_id) {
				dbAdapter.addAnswerToNote(noteId, question_id, other_id, other_text);
			}
			else {
				dbAdapter.addAnswerToNote(noteId, question_id, answer_ids[answerIndex]);
			}
		}
	}

	/**
	 * Enters the MultiSelectionSpinner selections into the database
	 * @param spinner
	 * @param dbAdapter
	 * @param question_id
	 * @param answers
	 */
	public void put(MultiSelectionSpinner spinner,
			int question_id, int[] answers, int answerOther) {
		List<Integer> selectedIndicies = spinner.getSelectedIndicies();
		for (int index : selectedIndicies) {
			if ((answerOther >= 0) && (answers[index] == answerOther)) {
				dbAdapter.addAnswerToNote(noteId, question_id, answers[index], spinner.getOtherText());
			}
			else {
				dbAdapter.addAnswerToNote(noteId, question_id, answers[index]);
			}
		}
	}
}
