package com.aegamesi.steamtrade.libs

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 ARNAUD FRUGIER
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import android.database.Cursor
import androidx.recyclerview.widget.RecyclerView
import com.aegamesi.steamtrade.steam.DBHelper.ChatEntry.Companion.ID

abstract class CursorRecyclerAdapter<VH : androidx.recyclerview.widget.RecyclerView.ViewHolder>(c: Cursor?) : androidx.recyclerview.widget.RecyclerView.Adapter<VH>() {

    private var mDataValid: Boolean = false
    private var mCursor: Cursor? = null
    private var mRowIDColumn: Int = 0

    init {
        init(c)
    }

    private fun init(c: Cursor?) {
        val cursorPresent = c != null
        mCursor = c
        mDataValid = cursorPresent
        mRowIDColumn = if (cursorPresent) c!!.getColumnIndexOrThrow("_id") else -1
        setHasStableIds(true)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        if (!mDataValid) {
            throw IllegalStateException("this should only be called when the cursor is valid")
        }
        if (!mCursor!!.moveToPosition(position)) {
            throw IllegalStateException("couldn't move cursor to position $position")
        }

        onBindViewHolder(holder, mCursor!!)
    }

    abstract fun onBindViewHolder(holder: VH, cursor: Cursor)

    override fun getItemCount(): Int {
        return if (mDataValid && mCursor != null) {
            mCursor!!.count
        } else {
            0
        }
    }

    override fun getItemId(position: Int): Long {
        return if (hasStableIds() && mDataValid && mCursor != null) {
            if (mCursor!!.moveToPosition(position)) {
                mCursor!!.getLong(mRowIDColumn)
            } else {
                androidx.recyclerview.widget.RecyclerView.NO_ID
            }
        } else {
            androidx.recyclerview.widget.RecyclerView.NO_ID
        }
    }

    /**
     * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
     * closed.
     *
     * @param cursor The new cursor to be used
     */
    fun changeCursor(cursor: Cursor?) {
        val old = swapCursor(cursor)
        old?.close()
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * [.changeCursor], the returned old Cursor is *not*
     * closed.
     *
     * @param newCursor The new cursor to be used.
     * @return Returns the previously set Cursor, or null if there was not one.
     * If the given new Cursor is the same instance is the previously set
     * Cursor, null is also returned.
     */
    private fun swapCursor(newCursor: Cursor?): Cursor? {
        if (newCursor === mCursor) {
            return null
        }
        val oldCursor = mCursor
        mCursor = newCursor
        if (newCursor != null) {
            mRowIDColumn = newCursor.getColumnIndexOrThrow("_id")
            mDataValid = true
            // notify the observers about the new cursor
            notifyDataSetChanged()
        } else {
            mRowIDColumn = -1
            mDataValid = false
            // notify the observers about the lack of a data set
            notifyItemRangeRemoved(0, itemCount)
        }
        return oldCursor
    }

}