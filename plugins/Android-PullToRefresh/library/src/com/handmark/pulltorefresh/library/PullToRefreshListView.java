/*******************************************************************************
 * Copyright 2011, 2012 Chris Banes.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.handmark.pulltorefresh.library;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.graphics.Color;

import com.handmark.pulltorefresh.library.internal.EmptyViewMethodAccessor;
import com.handmark.pulltorefresh.library.internal.LoadingLayout;

import com.firebase.client.Firebase;

public class PullToRefreshListView extends PullToRefreshAdapterViewBase<ListView> {

	private LoadingLayout mHeaderLoadingView;
	private LoadingLayout mFooterLoadingView;

	private FrameLayout mLvFooterLoadingFrame;

	private boolean mListViewExtrasEnabled;

    static Leitner leitner;
    static PTLLogger logger;

	public PullToRefreshListView(Context context) {
		super(context);
	}

	public PullToRefreshListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PullToRefreshListView(Context context, Mode mode) {
		super(context, mode);
	}

	public PullToRefreshListView(Context context, Mode mode, AnimationStyle style) {
		super(context, mode, style);
	}

    public void setLeitner(Leitner _leitner) {
        leitner = _leitner;
    }

    public void setLogger(PTLLogger _logger) {
        logger = _logger;
    }

	@Override
	public final Orientation getPullToRefreshScrollDirection() {
		return Orientation.VERTICAL;
	}

    // TODO:
    static boolean isFirstExercise = true;

	@Override
	protected void onRefreshing(final boolean doScroll) {
        if (isFirstExercise) {
            showNextExercise();
            isFirstExercise = false;
        }
		/**
		 * If we're not showing the Refreshing view, or the list is empty, the
		 * the header/footer views won't show so we use the normal method.
		 */
		ListAdapter adapter = mRefreshableView.getAdapter();
		if (!mListViewExtrasEnabled || !getShowViewWhileRefreshing() || null == adapter || adapter.isEmpty()) {
			super.onRefreshing(doScroll);
			return;
		}

		super.onRefreshing(false);

		final LoadingLayout origLoadingView, listViewLoadingView, oppositeListViewLoadingView;
		final int selection, scrollToY;

		switch (getCurrentMode()) {
			case MANUAL_REFRESH_ONLY:
			case PULL_FROM_END:
				origLoadingView = getFooterLayout();
				listViewLoadingView = mFooterLoadingView;
				oppositeListViewLoadingView = mHeaderLoadingView;
				selection = mRefreshableView.getCount() - 1;
				scrollToY = getScrollY() - getFooterSize();
				break;
			case PULL_FROM_START:
			default:
				origLoadingView = getHeaderLayout();
				listViewLoadingView = mHeaderLoadingView;
				oppositeListViewLoadingView = mFooterLoadingView;
				selection = 0;
                // TODO:
				scrollToY = getScrollY() + getHeaderSize();
                //scrollToY = getScrollY() + 2*getHeaderSize();
				break;
		}

		// Hide our original Loading View
		origLoadingView.reset();
		origLoadingView.hideAllViews();

		// Make sure the opposite end is hidden too
		oppositeListViewLoadingView.setVisibility(View.GONE);

		// Show the ListView Loading View and set it to refresh.
		listViewLoadingView.setVisibility(View.VISIBLE);
		listViewLoadingView.refreshing();
        if (leitner.getPrompt() == null || leitner.getTarget() == null) {
            mHeaderLoadingView.mHeaderText.setText("Initializing...");
            mHeaderLoadingView.mHeaderPTLButtonReveal.setVisibility(View.GONE);
        }

        // TODO: Set REVEAL button.
        mHeaderLoadingView.mHeaderPTLButtonReveal.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                logger.logEngage();
                if (leitner.isFirstExposure()) {
                    mHeaderLoadingView.mHeaderPTLButtonYes.setText("ALREADY KNEW");
                    mHeaderLoadingView.mHeaderPTLButtonYes.setBackgroundColor(Color.rgb(162, 228, 184));
                    mHeaderLoadingView.mHeaderPTLButtonYes.setOnClickListener(
                            new View.OnClickListener() {
                                public void onClick(View v) {
                                    Log.i("BUTTON PRESS", "Already knew!");
                                    logger.logSubmit("alreadyknew");
                                    leitner.onAlreadyKnew(new onPostTransactionCallback());
                                    Log.e("BUTTON PRESS", "Removing handler callback of: "
                                            + onRefreshCompleteRunnable + onRefreshCompleteHandler);
                                    onRefreshCompleteHandler.removeCallbacks(
                                            onRefreshCompleteRunnable);
                                    onRefreshCompleteHandler.post(onRefreshCompleteRunnable);
                                    onReset();
                                }
                            }
                    );
                    mHeaderLoadingView.mHeaderPTLButtonNo.setText("DIDN'T KNOW");
                    mHeaderLoadingView.mHeaderPTLButtonNo.setBackgroundColor(Color.rgb(255, 163, 181));
                    mHeaderLoadingView.mHeaderPTLButtonNo.setOnClickListener(
                            new View.OnClickListener() {
                                public void onClick(View v) {
                                    Log.i("BUTTON PRESS", "DIDN'T KNOW! Promoting item...");
                                    logger.logSubmit("didntknow");
                                    leitner.promote(new onPostTransactionCallback());
                                    Log.e("BUTTON PRESS", "Removing handler callback of: "
                                            + onRefreshCompleteRunnable + onRefreshCompleteHandler);
                                    onRefreshCompleteHandler.removeCallbacks(
                                            onRefreshCompleteRunnable);
                                    onRefreshCompleteHandler.post(onRefreshCompleteRunnable);
                                    onReset();
                                }
                            }
                    );
                } else {
                    mHeaderLoadingView.mHeaderPTLButtonYes.setText("I WAS RIGHT");
                    mHeaderLoadingView.mHeaderPTLButtonYes.setBackgroundColor(Color.GREEN);
                    mHeaderLoadingView.mHeaderPTLButtonYes.setOnClickListener(
                            new View.OnClickListener() {
                                public void onClick(View v) {
                                    Log.i("BUTTON PRESS", "Got it right.");
                                    logger.logSubmit("yes");
                                    Log.e("BUTTON PRESS", "Removing handler callback of: "
                                            + onRefreshCompleteRunnable + onRefreshCompleteHandler);
                                    onRefreshCompleteHandler.removeCallbacks(
                                            onRefreshCompleteRunnable);
                                    onRefreshCompleteHandler.post(onRefreshCompleteRunnable);
                                    leitner.promote(new onPostTransactionCallback());
                                    onReset();
                                }
                            }
                    );
                    mHeaderLoadingView.mHeaderPTLButtonNo.setText("I WAS WRONG");
                    mHeaderLoadingView.mHeaderPTLButtonNo.setBackgroundColor(Color.RED);
                    mHeaderLoadingView.mHeaderPTLButtonNo.setOnClickListener(
                            new View.OnClickListener() {
                                public void onClick(View v) {
                                    Log.i("BUTTON PRESS", "Got it wrong.");
                                    logger.logSubmit("no");
                                    Log.e("BUTTON PRESS", "Removing handler callback of: "
                                            +onRefreshCompleteRunnable+ onRefreshCompleteHandler);
                                    onRefreshCompleteHandler.removeCallbacks(
                                            onRefreshCompleteRunnable);
                                    onRefreshCompleteHandler.post(onRefreshCompleteRunnable);
                                    leitner.demote(new onPostTransactionCallback());
                                    onReset();
                                }
                            }
                    );
                }
                mHeaderLoadingView.mHeaderPTLButtonReveal.setVisibility(View.GONE);
                mHeaderLoadingView.mHeaderPTLTextTarget.setVisibility(View.VISIBLE);
                mHeaderLoadingView.mHeaderPTLPanelFeedback.setVisibility(View.VISIBLE);
            }
        });

		if (doScroll) {
			// We need to disable the automatic visibility changes for now
			disableLoadingLayoutVisibilityChanges();

			// We scroll slightly so that the ListView's header/footer is at the
			// same Y position as our normal header/footer
			setHeaderScroll(scrollToY);

			// Make sure the ListView is scrolled to show the loading
			// header/footer
			mRefreshableView.setSelection(selection);

			// Smooth scroll as normal
            //TODO:
			smoothScrollTo(0);
            //smoothScrollTo(scrollToY);
		}
	}

	@Override
	protected void onReset() {
		/**
		 * If the extras are not enabled, just call up to super and return.
		 */
		if (!mListViewExtrasEnabled) {
			super.onReset();
			return;
		}

		final LoadingLayout originalLoadingLayout, listViewLoadingLayout;
		final int scrollToHeight, selection;
		final boolean scrollLvToEdge;

		switch (getCurrentMode()) {
			case MANUAL_REFRESH_ONLY:
			case PULL_FROM_END:
				originalLoadingLayout = getFooterLayout();
				listViewLoadingLayout = mFooterLoadingView;
				selection = mRefreshableView.getCount() - 1;
				scrollToHeight = getFooterSize();
				scrollLvToEdge = Math.abs(mRefreshableView.getLastVisiblePosition() - selection) <= 1;
				break;
			case PULL_FROM_START:
			default:
				originalLoadingLayout = getHeaderLayout();
				listViewLoadingLayout = mHeaderLoadingView;
                // TODO:
				scrollToHeight = -getHeaderSize();
				selection = 0;
				scrollLvToEdge = Math.abs(mRefreshableView.getFirstVisiblePosition() - selection) <= 1;
				break;
		}

		// If the ListView header loading layout is showing, then we need to
		// flip so that the original one is showing instead
		if (listViewLoadingLayout.getVisibility() == View.VISIBLE) {

			// Set our Original View to Visible
			originalLoadingLayout.showInvisibleViews();

			// Hide the ListView Header/Footer
			listViewLoadingLayout.setVisibility(View.GONE);

			/**
			 * Scroll so the View is at the same Y as the ListView
			 * header/footer, but only scroll if: we've pulled to refresh, it's
			 * positioned correctly
			 */
			if (scrollLvToEdge && getState() != State.MANUAL_REFRESHING) {
				mRefreshableView.setSelection(selection);
				setHeaderScroll(scrollToHeight);
			}
		}
        // TODO: Reset REVEAL button.
        if (View.VISIBLE == mHeaderLoadingView.mHeaderPTLTextTarget.getVisibility()) {
            mHeaderLoadingView.mHeaderPTLTextTarget.setVisibility(View.GONE);
        }
        if (View.GONE == mHeaderLoadingView.mHeaderPTLButtonReveal.getVisibility() ||
            View.INVISIBLE == mHeaderLoadingView.mHeaderPTLButtonReveal.getVisibility()) {
            mHeaderLoadingView.mHeaderPTLButtonReveal.setVisibility(View.VISIBLE);
        }
        if (View.VISIBLE == mHeaderLoadingView.mHeaderPTLPanelFeedback.getVisibility()) {
            mHeaderLoadingView.mHeaderPTLPanelFeedback.setVisibility(View.GONE);
        }
        if (leitner != null && leitner.getPrompt() != null) {
            mHeaderLoadingView.setRefreshingLabel(leitner.getPrompt());
            mHeaderLoadingView.mHeaderText.setText(leitner.getPrompt());
        }
		// Finally, call up to super
		super.onReset();
	}

	@Override
	protected LoadingLayoutProxy createLoadingLayoutProxy(final boolean includeStart, final boolean includeEnd) {
		LoadingLayoutProxy proxy = super.createLoadingLayoutProxy(includeStart, includeEnd);

		if (mListViewExtrasEnabled) {
			final Mode mode = getMode();

			if (includeStart && mode.showHeaderLoadingLayout()) {
				proxy.addLayout(mHeaderLoadingView);
			}
			if (includeEnd && mode.showFooterLoadingLayout()) {
				proxy.addLayout(mFooterLoadingView);
			}
		}

		return proxy;
	}

	protected ListView createListView(Context context, AttributeSet attrs) {
		final ListView lv;
		if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD) {
			lv = new InternalListViewSDK9(context, attrs);
		} else {
			lv = new InternalListView(context, attrs);
		}
		return lv;
	}

	@Override
	protected ListView createRefreshableView(Context context, AttributeSet attrs) {
		ListView lv = createListView(context, attrs);

		// Set it to this so it can be used in ListActivity/ListFragment
		lv.setId(android.R.id.list);
		return lv;
	}

	@Override
	protected void handleStyledAttributes(TypedArray a) {
		super.handleStyledAttributes(a);

		mListViewExtrasEnabled = a.getBoolean(R.styleable.PullToRefresh_ptrListViewExtrasEnabled, true);

		if (mListViewExtrasEnabled) {
			final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);

			// Create Loading Views ready for use later
			FrameLayout frame = new FrameLayout(getContext());
			mHeaderLoadingView = createLoadingLayout(getContext(), Mode.PULL_FROM_START, a);
			mHeaderLoadingView.setVisibility(View.GONE);
			frame.addView(mHeaderLoadingView, lp);
			mRefreshableView.addHeaderView(frame, null, false);

			mLvFooterLoadingFrame = new FrameLayout(getContext());
			mFooterLoadingView = createLoadingLayout(getContext(), Mode.PULL_FROM_END, a);
			mFooterLoadingView.setVisibility(View.GONE);
			mLvFooterLoadingFrame.addView(mFooterLoadingView, lp);

			/**
			 * If the value for Scrolling While Refreshing hasn't been
			 * explicitly set via XML, enable Scrolling While Refreshing.
			 */
			if (!a.hasValue(R.styleable.PullToRefresh_ptrScrollingWhileRefreshingEnabled)) {
				setScrollingWhileRefreshingEnabled(true);
			}
		}
	}

	@TargetApi(9)
	final class InternalListViewSDK9 extends InternalListView {

		public InternalListViewSDK9(Context context, AttributeSet attrs) {
			super(context, attrs);
		}

		@Override
		protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX,
				int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {

			final boolean returnValue = super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX,
					scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);

			// Does all of the hard work...
			OverscrollHelper.overScrollBy(PullToRefreshListView.this, deltaX, scrollX, deltaY, scrollY, isTouchEvent);

			return returnValue;
		}
	}

	protected class InternalListView extends ListView implements EmptyViewMethodAccessor {

		private boolean mAddedLvFooter = false;

		public InternalListView(Context context, AttributeSet attrs) {
			super(context, attrs);
		}

		@Override
		protected void dispatchDraw(Canvas canvas) {
			/**
			 * This is a bit hacky, but Samsung's ListView has got a bug in it
			 * when using Header/Footer Views and the list is empty. This masks
			 * the issue so that it doesn't cause an FC. See Issue #66.
			 */
			try {
				super.dispatchDraw(canvas);
			} catch (IndexOutOfBoundsException e) {
				e.printStackTrace();
			}
		}

		@Override
		public boolean dispatchTouchEvent(MotionEvent ev) {
			/**
			 * This is a bit hacky, but Samsung's ListView has got a bug in it
			 * when using Header/Footer Views and the list is empty. This masks
			 * the issue so that it doesn't cause an FC. See Issue #66.
			 */
			try {
				return super.dispatchTouchEvent(ev);
			} catch (IndexOutOfBoundsException e) {
				e.printStackTrace();
				return false;
			}
		}

		@Override
		public void setAdapter(ListAdapter adapter) {
			// Add the Footer View at the last possible moment
			if (null != mLvFooterLoadingFrame && !mAddedLvFooter) {
				addFooterView(mLvFooterLoadingFrame, null, false);
				mAddedLvFooter = true;
			}

			super.setAdapter(adapter);
		}

		@Override
		public void setEmptyView(View emptyView) {
			PullToRefreshListView.this.setEmptyView(emptyView);
		}

		@Override
		public void setEmptyViewInternal(View emptyView) {
			super.setEmptyView(emptyView);
		}
	}

    private class onGotNextItemCallback implements ICallback {
        @Override
        public void callback(String string) {
            Log.i("MAIN CALLBACK", "Got next item: " + string);
            String exposure = "notfirst";
            if (leitner == null) {
                mHeaderLoadingView.setRefreshingLabel("Initializing...");
                mHeaderLoadingView.mHeaderText.setText("Initializing...");
                mHeaderLoadingView.mHeaderPTLButtonReveal.setVisibility(View.GONE);
            } else {
                if (leitner.isFirstExposure()) {
                    exposure = "first";
                }
                logger.updateState(exposure, leitner.getItemId(), leitner.getCurrentBucket());
                if (leitner.getPrompt() == null) {
                    mHeaderLoadingView.setRefreshingLabel("Initializing...");
                    mHeaderLoadingView.mHeaderText.setText("Initializing...");
                    mHeaderLoadingView.mHeaderPTLButtonReveal.setVisibility(View.GONE);
                    mHeaderLoadingView.mHeaderPTLTextTarget.setText("[]");
                    mHeaderLoadingView.setTargetLabel("[]");
                    logger.logDebug("missingprompt");
                } else {
                    mHeaderLoadingView.setRefreshingLabel(leitner.getPrompt());
                    mHeaderLoadingView.mHeaderText.setText(leitner.getPrompt());
                    mHeaderLoadingView.mHeaderPTLButtonReveal.setVisibility(View.VISIBLE);
                }

                if (leitner.getTarget() == null) {
                    mHeaderLoadingView.setRefreshingLabel("Initializing...");
                    mHeaderLoadingView.mHeaderText.setText("Initializing...");
                    mHeaderLoadingView.mHeaderPTLButtonReveal.setVisibility(View.GONE);
                    mHeaderLoadingView.mHeaderPTLTextTarget.setText("[]");
                    mHeaderLoadingView.setTargetLabel("[]");
                    logger.logDebug("missingtarget");
                } else if (leitner.getPrompt() != null) {
                    mHeaderLoadingView.mHeaderPTLButtonReveal.setVisibility(View.VISIBLE);
                    mHeaderLoadingView.mHeaderPTLTextTarget.setText(leitner.getTarget());
                    mHeaderLoadingView.setTargetLabel(leitner.getTarget());
                }
            }
        }
    }

    private class onPostTransactionCallback implements ICallback {
        @Override
        public void callback(String string) {
            Log.i("FLASHCARD FINISHED CB", "Got: " + string);
            showNextExercise();
        }
    }

    // TODO: Show next exercise.

    public ICallback onGotNextItem = new onGotNextItemCallback();

    public void showNextExercise() {
        Log.i("MAIN", "Getting next exercise...");
        if (leitner == null) {
            Log.e("HUH", "HELLO? Leitner is null? Why? Getting next item??");
        }
        Log.e("HUH", "what about here?");
        Leitner.getNextItem(onGotNextItem);
    }

    public Handler onRefreshCompleteHandler;
    public Runnable onRefreshCompleteRunnable;

    public void setOnRefreshCompleteHandler(Handler handler, Runnable runnable) {
        onRefreshCompleteHandler = handler;
        onRefreshCompleteRunnable = runnable;
    }

    @Override
    public final void onScroll(final AbsListView view, final int firstVisibleItem,
                               final int visibleItemCount, final int totalItemCount) {
        if (firstVisibleItem > 0 && isRefreshing()) {
            Log.e("SCROLL LISTEN", "Time to get rid of refresh panel!");
            PTLLogger.logInterruptScroll();
            onRefreshCompleteHandler.removeCallbacks(onRefreshCompleteRunnable);
            onRefreshCompleteHandler.post(onRefreshCompleteRunnable);
        }
        super.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
    }

    public void hideLoading() {
        mHeaderLoadingView.hideLoadingDrawable();
    }
}
