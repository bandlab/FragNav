package com.ncapdevi.fragnav;

import android.os.Bundle;
import android.support.annotation.CheckResult;
import android.support.annotation.IdRes;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.Pair;
import android.view.View;

import org.json.JSONArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * The class is used to manage navigation through multiple stacks of fragments, as well as coordinate
 * fragments that may appear on screen
 * <p>
 * https://github.com/ncapdevi/FragNav
 * Nic Capdevila
 * Nic.Capdevila@gmail.com
 * <p>
 * Originally Created March 2016
 */
public class FragNavController {
    //Declare the constants  There is a maximum of 5 tabs, this is per Material Design's Bottom Navigation's design spec.
    public static final int NO_TAB = -1;
    public static final int TAB1 = 0;
    public static final int TAB2 = 1;
    public static final int TAB3 = 2;
    public static final int TAB4 = 3;
    public static final int TAB5 = 4;

    // Extras used to store savedInstanceState
    private static final String EXTRA_TAG_COUNT = FragNavController.class.getName() + ":EXTRA_TAG_COUNT";
    private static final String EXTRA_SELECTED_TAB_INDEX = FragNavController.class.getName() + ":EXTRA_SELECTED_TAB_INDEX";
    private static final String EXTRA_CURRENT_FRAGMENT = FragNavController.class.getName() + ":EXTRA_CURRENT_FRAGMENT";
    private static final String EXTRA_FRAGMENT_STACK = FragNavController.class.getName() + ":EXTRA_FRAGMENT_STACK";

    @IdRes
    private final int mContainerId;
    @NonNull
    private final List<Stack<Fragment>> mFragmentStacks;
    @NonNull
    private final FragmentManager mFragmentManager;

    @TabIndex
    private int mSelectedTabIndex = NO_TAB;
    private int mTagCount;

    @Nullable
    private Fragment mCurrentFrag;
    @Nullable
    private DialogFragment mCurrentDialogFrag;

    @Nullable
    private RootFragmentListener mRootFragmentListener;

    @Nullable
    private TransactionListener mTransactionListener;

    @Transit
    private int mDefaultTransitionMode = FragmentTransaction.TRANSIT_UNSET;

    private boolean mExecutingTransaction;

    //region Construction and setup

    /**
     * @param fragmentManager FragmentManager to be used
     * @param containerId     The resource ID of the layout in which the fragments will be placed
     * @param numberOfTabs    The number of different fragment stacks to be managed (maximum of five)
     */
    private FragNavController(@NonNull FragmentManager fragmentManager, @IdRes int containerId, int numberOfTabs) {
        mFragmentManager = fragmentManager;
        mContainerId = containerId;
        mFragmentStacks = new ArrayList<>(numberOfTabs);
    }

    /**
     * @param savedInstanceState savedInstanceState to allow for recreation of FragNavController and its fragments if possible
     * @param fragmentManager    FragmentManager to be used
     * @param containerId        The resource ID of the layout in which the fragments will be placed
     * @param rootFragment       A single root fragment. This library can still be helpful when mangiging a single stack of fragments.
     */

    public FragNavController(Bundle savedInstanceState, @NonNull FragmentManager fragmentManager, @IdRes int containerId, @NonNull Fragment rootFragment) {
        this(fragmentManager, containerId, 1);

        //Attempt to restore from bundle, if not, initialize
        List<Fragment> rootFragments = new ArrayList<>(1);
        rootFragments.add(rootFragment);

        if (!restoreFromBundle(savedInstanceState, rootFragments)) {
            Stack<Fragment> stack = new Stack<>();
            stack.add(rootFragment);
            mFragmentStacks.add(stack);
            initialize(TAB1);
        }
    }

    /**
     * @param savedInstanceState savedInstanceState to allow for recreation of FragNavController and its fragments if possible
     * @param fragmentManager    FragmentManager to be used
     * @param containerId        The resource ID of the layout in which the fragments will be placed
     * @param rootFragments      a list of root fragments. root Fragments are the root fragments that exist on any tab structure. If only one fragment is sent
     *                           in,
     *                           fragnav will still manage transactions
     * @param startingIndex      The initial tab index to be used must be in range of rootFragments size
     */
    public FragNavController(Bundle savedInstanceState, @NonNull FragmentManager fragmentManager, @IdRes int containerId, @NonNull List<Fragment> rootFragments, @TabIndex int startingIndex) {
        this(savedInstanceState, fragmentManager, containerId, rootFragments, startingIndex, null);
    }

    /**
     * @param savedInstanceState savedInstanceState to allow for recreation of FragNavController and its fragments if possible
     * @param fragmentManager    FragmentManager to be used
     * @param containerId        The resource ID of the layout in which the fragments will be placed
     * @param rootFragments      a list of root fragments. root Fragments are the root fragments that exist on any tab structure. If only one fragment is sent
     *                           in,
     *                           fragnav will still manage transactions
     * @param startingIndex      The initial tab index to be used must be in range of rootFragments size
     */
    public FragNavController(Bundle savedInstanceState, @NonNull FragmentManager fragmentManager, @IdRes int containerId, @NonNull List<Fragment> rootFragments, @TabIndex int startingIndex,
                             TransactionListener transactionListener) {
        this(fragmentManager, containerId, rootFragments.size());

        if (startingIndex > rootFragments.size()) {
            throw new IndexOutOfBoundsException("Starting index cannot be larger than the number of stacks");
        }

        setTransactionListener(transactionListener);

        //Attempt to restore from bundle, if not, initialize
        if (!restoreFromBundle(savedInstanceState, rootFragments)) {
            for (Fragment fragment : rootFragments) {
                Stack<Fragment> stack = new Stack<>();
                stack.add(fragment);
                mFragmentStacks.add(stack);
            }
            initialize(startingIndex);
        }
    }

    /**
     * @param savedInstanceState   savedInstanceState to allow for recreation of FragNavController and its fragments if possible
     * @param fragmentManager      FragmentManager to be used
     * @param containerId          The resource ID of the layout in which the fragments will be placed
     * @param rootFragmentListener A listener that allows for dynamically creating root fragments
     * @param numberOfTabs         The number of different fragment stacks to be managed (maximum of five)
     * @param startingIndex        The initial tab index to be used must be in range of rootFragments size
     */
    public FragNavController(Bundle savedInstanceState, @NonNull FragmentManager fragmentManager, @IdRes int containerId, RootFragmentListener rootFragmentListener, int numberOfTabs,
                             @TabIndex int startingIndex) {
        this(savedInstanceState, fragmentManager, containerId, rootFragmentListener, numberOfTabs, startingIndex, null);
    }

    /**
     * @param savedInstanceState   savedInstanceState to allow for recreation of FragNavController and its fragments if possible
     * @param fragmentManager      FragmentManager to be used
     * @param containerId          The resource ID of the layout in which the fragments will be placed
     * @param rootFragmentListener A listener that allows for dynamically creating root fragments
     * @param numberOfTabs         The number of different fragment stacks to be managed (maximum of five)
     * @param startingIndex        The initial tab index to be used must be in range of rootFragments size
     * @param transactionListener  A listener to be implemented (typically within the main activity) to fragment transactions (including tab switches);
     */
    public FragNavController(Bundle savedInstanceState, @NonNull FragmentManager fragmentManager, @IdRes int containerId, RootFragmentListener rootFragmentListener, int numberOfTabs,
                             @TabIndex int startingIndex, TransactionListener transactionListener) {
        this(fragmentManager, containerId, numberOfTabs);

        if (startingIndex > numberOfTabs) {
            throw new IndexOutOfBoundsException("Starting index cannot be larger than the number of stacks");
        }

        setRootFragmentListener(rootFragmentListener);
        setTransactionListener(transactionListener);

        //Attempt to restore from bundle, if not, initialize
        if (!restoreFromBundle(savedInstanceState, null)) {
            for (int i = 0; i < numberOfTabs; i++) {
                mFragmentStacks.add(new Stack<Fragment>());
            }
            initialize(startingIndex);
        }
    }

    /**
     * @param rootFragmentListener a listener that allows for dynamically creating root fragments
     */
    public void setRootFragmentListener(RootFragmentListener rootFragmentListener) {
        mRootFragmentListener = rootFragmentListener;
    }

    /**
     * @param transactionListener A listener to be implemented (typically within the main activity) to fragment transactions (including tab switches);
     */
    public void setTransactionListener(TransactionListener transactionListener) {
        mTransactionListener = transactionListener;
    }

    /**
     * @param transitionMode The type of transition to be used during fragment transactions
     */
    public void setDefaultTransitionMode(@Transit int transitionMode) {
        mDefaultTransitionMode = transitionMode;
    }
    //endregion

    //region Transactions

    /**
     * Switch to a different tab. Should not be called on the current tab.
     *
     * @param index the index of the tab to switch to
     * @throws IndexOutOfBoundsException If the index to switch to is out of range
     */

    /**
     * Function used to switch to the specified fragment stack
     *
     * @param index              The given index to switch to
     * @param transactionOptions Transaction options to be displayed
     * @throws IndexOutOfBoundsException Thrown if trying to switch to an index outside given range
     */
    public void switchTab(@TabIndex int index, @Nullable FragNavTransactionOptions transactionOptions) throws IndexOutOfBoundsException {
        //Check to make sure the tab is within range
        if (index >= mFragmentStacks.size()) {
            throw new IndexOutOfBoundsException("Can't switch to a tab that hasn't been initialized, " +
                    "Index : " + index + ", current stack size : " + mFragmentStacks.size() +
                    ". Make sure to create all of the tabs you need in the Constructor or provide a way for them to be created via RootFragmentListener.");
        }
        if (mSelectedTabIndex != index) {
            mSelectedTabIndex = index;

            FragmentTransaction ft = createTransactionWithOptions(transactionOptions);

            detachCurrentFragment(ft);

            Fragment fragment = null;
            if (index == NO_TAB) {
                ft.commit();
            } else {
                //Attempt to reattach previous fragment
                fragment = reattachPreviousFragment(ft);
                if (fragment != null) {
                    ft.commit();
                } else {
                    fragment = getRootFragment(mSelectedTabIndex);
                    ft.add(mContainerId, fragment, generateTag(fragment));
                    ft.commit();
                }
            }

            executePendingTransactions();

            mCurrentFrag = fragment;
            if (mTransactionListener != null) {
                mTransactionListener.onTabTransaction(mCurrentFrag, mSelectedTabIndex);
            }
        }
    }

    /**
     * Function used to switch to the specified fragment stack
     *
     * @param index The given index to switch to
     * @throws IndexOutOfBoundsException Thrown if trying to switch to an index outside given range
     */
    public void switchTab(@TabIndex int index) throws IndexOutOfBoundsException {
        switchTab(index, null);
    }

    /**
     * Push a fragment onto the current stack
     *
     * @param fragment           The fragment that is to be pushed
     * @param transactionOptions Transaction options to be displayed
     */
    public void pushFragment(@Nullable Fragment fragment, @Nullable FragNavTransactionOptions transactionOptions) {
        if (fragment != null && mSelectedTabIndex != NO_TAB) {
            FragmentTransaction ft = createTransactionWithOptions(transactionOptions);

            detachCurrentFragment(ft);
            ft.add(mContainerId, fragment, generateTag(fragment));
            ft.commit();

            executePendingTransactions();

            mFragmentStacks.get(mSelectedTabIndex).push(fragment);

            mCurrentFrag = fragment;
            if (mTransactionListener != null) {
                mTransactionListener.onFragmentTransaction(mCurrentFrag);
            }

        }
    }

    /**
     * Push a fragment onto the current stack
     *
     * @param fragment The fragment that is to be pushed
     */
    public void pushFragment(@Nullable Fragment fragment) {
        pushFragment(fragment, null);
    }

    /**
     * Pop the current fragment from the current tab
     *
     * @param transactionOptions Transaction options to be displayed
     */
    public void popFragment(@Nullable FragNavTransactionOptions transactionOptions) throws UnsupportedOperationException {
        popFragments(1, transactionOptions);
    }

    /**
     * Pop the current fragment from the current tab
     */
    public void popFragment() throws UnsupportedOperationException {
        popFragment(null);
    }

    /**
     * Pop the current stack until a given tag is found. If the tag is not found, the stack will popFragment until it is at
     * the root fragment
     *
     * @param transactionOptions Transaction options to be displayed
     */
    public void popFragments(int popDepth, @Nullable FragNavTransactionOptions transactionOptions) throws UnsupportedOperationException {
        if (isRootFragment()) {
            throw new UnsupportedOperationException(
                    "You can not popFragment the rootFragment. If you need to change this fragment, use replaceFragment(fragment)");
        } else if (popDepth < 1) {
            throw new UnsupportedOperationException("popFragments parameter needs to be greater than 0");
        } else if (mSelectedTabIndex == NO_TAB) {
            throw new UnsupportedOperationException("You can not pop fragments when no tab is selected");
        }

        //If our popDepth is big enough that it would just clear the stack, then call that.
        if (popDepth >= mFragmentStacks.get(mSelectedTabIndex).size() - 1) {
            clearStack();
            return;
        }

        Fragment fragment;
        FragmentTransaction ft = createTransactionWithOptions(transactionOptions);

        //Pop the number of the fragments on the stack and remove them from the FragmentManager
        for (int i = 0; i < popDepth; i++) {
            fragment = mFragmentManager.findFragmentByTag(mFragmentStacks.get(mSelectedTabIndex).pop().getTag());
            if (fragment != null) {
                ft.remove(fragment);
            }
        }

        //Attempt to reattach previous fragment
        fragment = reattachPreviousFragment(ft);

        boolean bShouldPush = false;
        //If we can't reattach, either pull from the stack, or create a new root fragment
        if (fragment != null) {
            ft.commit();
        } else {
            if (!mFragmentStacks.get(mSelectedTabIndex).isEmpty()) {
                fragment = mFragmentStacks.get(mSelectedTabIndex).peek();
                ft.add(mContainerId, fragment, fragment.getTag());
                ft.commit();
            } else {
                fragment = getRootFragment(mSelectedTabIndex);
                ft.add(mContainerId, fragment, generateTag(fragment));
                ft.commit();

                bShouldPush = true;
            }
        }

        executePendingTransactions();

        //Need to have this down here so that that tag has been
        // committed to the fragment before we add to the stack
        if (bShouldPush) {
            mFragmentStacks.get(mSelectedTabIndex).push(fragment);
        }

        mCurrentFrag = fragment;
        if (mTransactionListener != null) {
            mTransactionListener.onFragmentTransaction(mCurrentFrag);
        }
    }

    /**
     * Pop the current fragment from the current tab
     */
    public void popFragments(int popDepth) throws UnsupportedOperationException {
        popFragments(popDepth, null);
    }

    /**
     * Clears the current tab's stack to get to just the bottom Fragment. This will reveal the root fragment
     *
     * @param transactionOptions Transaction options to be displayed
     */
    public void clearStack(@Nullable FragNavTransactionOptions transactionOptions) {
        if (mSelectedTabIndex == NO_TAB) {
            return;
        }

        //Grab Current stack
        Stack<Fragment> fragmentStack = mFragmentStacks.get(mSelectedTabIndex);

        // Only need to start popping and reattach if the stack is greater than 1
        if (fragmentStack.size() > 1) {
            Fragment fragment;
            FragmentTransaction ft = createTransactionWithOptions(transactionOptions);

            //Pop all of the fragments on the stack and remove them from the FragmentManager
            while (fragmentStack.size() > 1) {
                fragment = mFragmentManager.findFragmentByTag(fragmentStack.pop().getTag());
                if (fragment != null) {
                    ft.remove(fragment);
                }
            }

            //Attempt to reattach previous fragment
            fragment = reattachPreviousFragment(ft);

            boolean bShouldPush = false;
            //If we can't reattach, either pull from the stack, or create a new root fragment
            if (fragment != null) {
                ft.commit();
            } else {
                if (!fragmentStack.isEmpty()) {
                    fragment = fragmentStack.peek();
                    ft.add(mContainerId, fragment, fragment.getTag());
                    ft.commit();
                } else {
                    fragment = getRootFragment(mSelectedTabIndex);
                    ft.add(mContainerId, fragment, generateTag(fragment));
                    ft.commit();

                    bShouldPush = true;
                }
            }

            executePendingTransactions();

            if (bShouldPush) {
                mFragmentStacks.get(mSelectedTabIndex).push(fragment);
            }

            //Update the stored version we have in the list
            mFragmentStacks.set(mSelectedTabIndex, fragmentStack);

            mCurrentFrag = fragment;
            if (mTransactionListener != null) {
                mTransactionListener.onFragmentTransaction(mCurrentFrag);
            }
        }
    }

    /**
     * Clears the current tab's stack to get to just the bottom Fragment. This will reveal the root fragment,
     */
    public void clearStack() {
        clearStack(null);
    }

    /**
     * Replace the current fragment
     *
     * @param fragment           the fragment to be shown instead
     * @param transactionOptions Transaction options to be displayed
     */
    public void replaceFragment(@NonNull Fragment fragment, @Nullable FragNavTransactionOptions transactionOptions) {
        Fragment poppingFrag = getCurrentFrag();

        if (poppingFrag != null) {
            FragmentTransaction ft = createTransactionWithOptions(transactionOptions);

            //overly cautious fragment popFragment
            Stack<Fragment> fragmentStack = mFragmentStacks.get(mSelectedTabIndex);
            if (!fragmentStack.isEmpty()) {
                fragmentStack.pop();
            }

            String tag = generateTag(fragment);
            ft.replace(mContainerId, fragment, tag);

            //Commit our transactions
            ft.commit();

            executePendingTransactions();

            fragmentStack.push(fragment);
            mCurrentFrag = fragment;

            if (mTransactionListener != null) {
                mTransactionListener.onFragmentTransaction(mCurrentFrag);

            }
        }
    }

    /**
     * Replace the current fragment
     *
     * @param fragment the fragment to be shown instead
     */
    public void replaceFragment(@NonNull Fragment fragment) {
        replaceFragment(fragment, null);
    }
    //endregion

    //region Private helper functions

    /**
     * Helper function to make sure that we are starting with a clean slate and to perform our first fragment interaction.
     *
     * @param index the tab index to initialize to
     */
    private void initialize(@TabIndex int index) {
        mSelectedTabIndex = index;
        clearFragmentManager();
        clearDialogFragment();

        if (index == NO_TAB) {
            return;
        }

        FragmentTransaction ft = mFragmentManager.beginTransaction();
        ft.setTransition(mDefaultTransitionMode);

        Fragment fragment = getRootFragment(index);
        ft.add(mContainerId, fragment, generateTag(fragment));
        ft.commit();

        executePendingTransactions();

        mCurrentFrag = fragment;
        if (mTransactionListener != null) {
            mTransactionListener.onTabTransaction(mCurrentFrag, mSelectedTabIndex);
        }
    }

    /**
     * Helper function to get the root fragment for a given index. This is done by either passing them in the constructor, or dynamically via NavListner
     *
     * @param index The tab index to get this fragment from
     * @return The root fragment at this index
     * @throws IllegalStateException This will be thrown if we can't find a rootFragment for this index. Either because you didn't provide it in the
     *                               constructor, or because your RootFragmentListener.getRootFragment(index) isn't returning a fragment for this index.
     */
    @NonNull
    @CheckResult
    private Fragment getRootFragment(int index) throws IllegalStateException {
        Fragment fragment = null;
        if (!mFragmentStacks.get(index).isEmpty()) {
            fragment = mFragmentStacks.get(index).peek();
        } else if (mRootFragmentListener != null) {
            fragment = mRootFragmentListener.getRootFragment(index);

            if (mSelectedTabIndex != NO_TAB) {
                mFragmentStacks.get(mSelectedTabIndex).push(fragment);
            }

        }
        if (fragment == null) {
            throw new IllegalStateException("Either you haven't past in a fragment at this index in your constructor, or you haven't" +
                    "provided a way to create it while via your RootFragmentListener.getRootFragment(index)");
        }

        return fragment;
    }

    /**
     * Will attempt to reattach a previous fragment in the FragmentManager, or return null if not able to,
     *
     * @param ft current fragment transaction
     * @return Fragment if we were able to find and reattach it
     */
    @Nullable
    private Fragment reattachPreviousFragment(@NonNull FragmentTransaction ft) {
        Stack<Fragment> fragmentStack = mFragmentStacks.get(mSelectedTabIndex);
        Fragment fragment = null;
        if (!fragmentStack.isEmpty()) {
            fragment = mFragmentManager.findFragmentByTag(fragmentStack.peek().getTag());
            if (fragment != null) {
                ft.attach(fragment);
            }
        }
        return fragment;
    }

    /**
     * Attemps to detach any current fragment if it exists, and if none is found, returns;
     *
     * @param ft the current transaction being performed
     */
    private void detachCurrentFragment(@NonNull FragmentTransaction ft) {
        Fragment oldFrag = getCurrentFrag();
        if (oldFrag != null) {
            ft.detach(oldFrag);
        }
    }

    /**
     * Helper function to attempt to get current fragment
     *
     * @return Fragment the current frag to be returned
     */
    @Nullable
    @CheckResult
    public Fragment getCurrentFrag() {
        //Attempt to used stored current fragment
        if (mCurrentFrag != null) {
            return mCurrentFrag;
        } else if (mSelectedTabIndex == NO_TAB) {
            return null;
        }
        //if not, try to pull it from the stack
        else {
            Stack<Fragment> fragmentStack = mFragmentStacks.get(mSelectedTabIndex);
            if (!fragmentStack.isEmpty()) {
                mCurrentFrag = mFragmentManager.findFragmentByTag(mFragmentStacks.get(mSelectedTabIndex).peek().getTag());
            }
        }
        return mCurrentFrag;
    }

    /**
     * Create a unique fragment tag so that we can grab the fragment later from the FragmentManger
     *
     * @param fragment The fragment that we're creating a unique tag for
     * @return a unique tag using the fragment's class name
     */
    @NonNull
    @CheckResult
    private String generateTag(@NonNull Fragment fragment) {
        return fragment.getClass().getName() + ++mTagCount;
    }

    /**
     * This check is here to prevent recursive entries into executePendingTransactions
     */
    private void executePendingTransactions() {
        if (!mExecutingTransaction) {
            mExecutingTransaction = true;
            mFragmentManager.executePendingTransactions();
            mExecutingTransaction = false;
        }
    }

    /**
     * Private helper function to clear out the fragment manager on initialization. All fragment management should be done via FragNav
     */
    private void clearFragmentManager() {
        if (mFragmentManager.getFragments() != null) {
            FragmentTransaction ft = mFragmentManager.beginTransaction();
            ft.setTransition(mDefaultTransitionMode);
            for (Fragment fragment : mFragmentManager.getFragments()) {
                if (fragment != null) {
                    ft.remove(fragment);
                }
            }
            ft.commit();
            executePendingTransactions();
        }
    }

    /**
     * Setup a fragment transaction with the given option
     *
     * @param transactionOptions The options that will be set for this transaction
     */
    @CheckResult
    private FragmentTransaction createTransactionWithOptions(@Nullable FragNavTransactionOptions transactionOptions) {
        FragmentTransaction ft = mFragmentManager.beginTransaction();
        if (transactionOptions != null) {

            ft.setCustomAnimations(transactionOptions.enterAnimation, transactionOptions.exitAnimation, transactionOptions.popEnterAnimation, transactionOptions.popExitAnimation);
            ft.setTransitionStyle(transactionOptions.transitionStyle);

            if (transactionOptions.transition != null) {
                ft.setTransition(transactionOptions.transition);
            } else {
                ft.setTransition(mDefaultTransitionMode);
            }

            if (transactionOptions.sharedElements != null) {
                for (Pair<View, String> sharedElement : transactionOptions.sharedElements) {
                    ft.addSharedElement(sharedElement.first, sharedElement.second);
                }
            }


            if (transactionOptions.breadCrumbTitle != null) {
                ft.setBreadCrumbTitle(transactionOptions.breadCrumbTitle);
            }

            if (transactionOptions.breadCrumbShortTitle != null) {
                ft.setBreadCrumbShortTitle(transactionOptions.breadCrumbShortTitle);

            }
        }
        return ft;
    }

    //endregion

    //region Public helper functions

    /**
     * Get the number of fragment stacks
     *
     * @return the number of fragment stacks
     */
    @CheckResult
    public int getSize() {
        return mFragmentStacks.size();
    }

    /**
     * Get a copy of the current stack that is being displayed
     *
     * @return Current stack
     */
    @CheckResult
    @NonNull
    public Stack<Fragment> getCurrentStack() {
        if (mSelectedTabIndex == NO_TAB) return null;

        return (Stack<Fragment>) mFragmentStacks.get(mSelectedTabIndex).clone();
    }

    /**
     * Get the index of the current stack that is being displayed
     *
     * @return Current stack index
     */
    @CheckResult
    @TabIndex
    public int getCurrentStackIndex() {
        return mSelectedTabIndex;
    }

    /**
     * @return If you are able to popFragment the current stack. If false, you are at the bottom of the stack
     * (Consider using replaceFragment if you need to change the root fragment for some reason)
     * * @deprecated use {@link #isRootFragment()} instead. Changed for naming reasons
     */
    @Deprecated
    @CheckResult
    public boolean canPop() {
        return getCurrentStack().size() > 1;
    }

    /**
     * @return If true, you are at the bottom of the stack
     * (Consider using replaceFragment if you need to change the root fragment for some reason)
     * else you can popFragment as needed as your are not at the root
     * * @deprecated use {@link #isRootFragment()} instead.
     */
    @CheckResult
    public boolean isRootFragment() {
        Stack<Fragment> stack = getCurrentStack();

        return stack == null || stack.size() == 1;
    }

    /**
     * @return Current DialogFragment being displayed. Null if none
     */
    @Nullable
    @CheckResult
    public DialogFragment getCurrentDialogFrag() {
        if (mCurrentDialogFrag != null) {
            return mCurrentDialogFrag;
        }
        //Else try to find one in the fragmentmanager
        else {
            FragmentManager fragmentManager;
            if (mCurrentFrag != null) {
                fragmentManager = mCurrentFrag.getChildFragmentManager();
            } else {
                fragmentManager = mFragmentManager;
            }
            if (fragmentManager.getFragments() != null) {
                for (Fragment fragment : fragmentManager.getFragments()) {
                    if (fragment instanceof DialogFragment) {
                        mCurrentDialogFrag = (DialogFragment) fragment;
                        break;
                    }
                }
            }
        }
        return mCurrentDialogFrag;
    }

    /**
     * Clear any DialogFragments that may be shown
     */
    public void clearDialogFragment() {
        if (mCurrentDialogFrag != null) {
            mCurrentDialogFrag.dismiss();
            mCurrentDialogFrag = null;
        }
        // If we don't have the current dialog, try to find and dismiss it
        else {
            FragmentManager fragmentManager;
            if (mCurrentFrag != null) {
                fragmentManager = mCurrentFrag.getChildFragmentManager();
            } else {
                fragmentManager = mFragmentManager;
            }

            if (fragmentManager.getFragments() != null) {
                for (Fragment fragment : fragmentManager.getFragments()) {
                    if (fragment instanceof DialogFragment) {
                        ((DialogFragment) fragment).dismiss();
                    }
                }
            }
        }
    }

    /**
     * Display a DialogFragment on the screen
     *
     * @param dialogFragment The Fragment to be Displayed
     */
    public void showDialogFragment(@Nullable DialogFragment dialogFragment) {
        if (dialogFragment != null) {
            FragmentManager fragmentManager;
            if (mCurrentFrag != null) {
                fragmentManager = mCurrentFrag.getChildFragmentManager();
            } else {
                fragmentManager = mFragmentManager;
            }

            //Clear any current dialogfragments
            if (fragmentManager.getFragments() != null) {
                for (Fragment fragment : fragmentManager.getFragments()) {
                    if (fragment instanceof DialogFragment) {
                        ((DialogFragment) fragment).dismiss();
                        mCurrentDialogFrag = null;
                    }
                }
            }

            mCurrentDialogFrag = dialogFragment;
            try {
                dialogFragment.show(fragmentManager, dialogFragment.getClass().getName());
            } catch (IllegalStateException e) {
                // Activity was likely destroyed before we had a chance to show, nothing can be done here.
            }
        }
    }

    //endregion

    //region SavedInstanceState

    /**
     * Call this in your Activity's onSaveInstanceState(Bundle outState) method to save the instance's state.
     *
     * @param outState The Bundle to save state information to
     */
    public void onSaveInstanceState(@NonNull Bundle outState) {

        // Write tag count
        outState.putInt(EXTRA_TAG_COUNT, mTagCount);

        // Write select tab
        outState.putInt(EXTRA_SELECTED_TAB_INDEX, mSelectedTabIndex);

        // Write current fragment
        if (mCurrentFrag != null) {
            outState.putString(EXTRA_CURRENT_FRAGMENT, mCurrentFrag.getTag());
        }

        // Write stacks
        try {
            final JSONArray stackArrays = new JSONArray();

            for (Stack<Fragment> stack : mFragmentStacks) {
                final JSONArray stackArray = new JSONArray();

                for (Fragment fragment : stack) {
                    stackArray.put(fragment.getTag());
                }

                stackArrays.put(stackArray);
            }

            outState.putString(EXTRA_FRAGMENT_STACK, stackArrays.toString());
        } catch (Throwable t) {
            // Nothing we can do
        }
    }

    /**
     * Restores this instance to the state specified by the contents of savedInstanceState
     *
     * @param savedInstanceState The bundle to restore from
     * @param rootFragments      List of root fragments from which to initialize empty stacks. If null, pull fragments from RootFragmentListener
     * @return true if successful, false if not
     */
    private boolean restoreFromBundle(@Nullable Bundle savedInstanceState, @Nullable List<Fragment> rootFragments) {
        if (savedInstanceState == null) {
            return false;
        }

        // Restore tag count
        mTagCount = savedInstanceState.getInt(EXTRA_TAG_COUNT, 0);

        // Restore current fragment
        mCurrentFrag = mFragmentManager.findFragmentByTag(savedInstanceState.getString(EXTRA_CURRENT_FRAGMENT));

        // Restore fragment stacks
        try {
            final JSONArray stackArrays = new JSONArray(savedInstanceState.getString(EXTRA_FRAGMENT_STACK));

            for (int x = 0; x < stackArrays.length(); x++) {
                final JSONArray stackArray = stackArrays.getJSONArray(x);
                final Stack<Fragment> stack = new Stack<>();

                if (stackArray.length() == 1) {
                    final String tag = stackArray.getString(0);
                    final Fragment fragment;

                    if (tag == null || "null".equalsIgnoreCase(tag)) {
                        if (rootFragments != null) {
                            fragment = rootFragments.get(x);
                        } else {
                            fragment = getRootFragment(x);
                        }

                    } else {
                        fragment = mFragmentManager.findFragmentByTag(tag);
                    }

                    if (fragment != null) {
                        stack.add(fragment);
                    }
                } else {
                    for (int y = 0; y < stackArray.length(); y++) {
                        final String tag = stackArray.getString(y);

                        if (tag != null && !"null".equalsIgnoreCase(tag)) {
                            final Fragment fragment = mFragmentManager.findFragmentByTag(tag);

                            if (fragment != null) {
                                stack.add(fragment);
                            }
                        }
                    }
                }

                mFragmentStacks.add(stack);
            }
            // Restore selected tab if we have one
            switch (savedInstanceState.getInt(EXTRA_SELECTED_TAB_INDEX)) {
                case TAB1:
                    switchTab(TAB1);
                    break;
                case TAB2:
                    switchTab(TAB2);
                    break;
                case TAB3:
                    switchTab(TAB3);
                    break;
                case TAB4:
                    switchTab(TAB4);
                    break;
                case TAB5:
                    switchTab(TAB5);
                    break;
            }

            //Succesfully restored state
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
    //endregion

    //Declare the TabIndex annotation
    @IntDef({NO_TAB, TAB1, TAB2, TAB3, TAB4, TAB5})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TabIndex {
    }

    // Declare Transit Styles
    @IntDef({FragmentTransaction.TRANSIT_NONE, FragmentTransaction.TRANSIT_FRAGMENT_OPEN, FragmentTransaction.TRANSIT_FRAGMENT_CLOSE, FragmentTransaction.TRANSIT_FRAGMENT_FADE
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface Transit {
    }

    public interface RootFragmentListener {
        /**
         * Dynamically create the Fragment that will go on the bottom of the stack
         *
         * @param index the index that the root of the stack Fragment needs to go
         * @return the new Fragment
         */
        Fragment getRootFragment(int index);
    }

    public interface TransactionListener {

        void onTabTransaction(Fragment fragment, int index);

        void onFragmentTransaction(Fragment fragment);
    }
}
