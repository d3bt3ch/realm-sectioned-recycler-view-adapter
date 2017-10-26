import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.SparseArray;

import java.util.Iterator;

import io.realm.OrderedCollectionChangeSet;
import io.realm.OrderedRealmCollection;
import io.realm.OrderedRealmCollectionChangeListener;
import io.realm.RealmList;
import io.realm.RealmModel;
import io.realm.RealmResults;

/**
 * Created by Debjit Kar (debjitk@hotmail.com) on 24/08/16.
 */
public abstract class RealmSectionedRecyclerViewAdapter<T extends RealmModel, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> implements ISectionedRecyclerViewAdapter {

    private boolean autoUpdate;
    private boolean updateOnModification;

    private int itemsPerSection;

    private OrderedRealmCollectionChangeListener listener;

    @Nullable
    private SparseArray<Section> sectionData;

    @Nullable
    private OrderedRealmCollection<T> adapterData;


    private OrderedRealmCollectionChangeListener createListener() {
        return new OrderedRealmCollectionChangeListener() {
            @Override
            public void onChange(Object collection, OrderedCollectionChangeSet changeSet) {
                // null Changes means the async query returns the first time.
                if (changeSet == null) {
                    notifyDataSetChanged();
                    return;
                }
                // For deletions, the adapter has to be notified in reverse order.
                OrderedCollectionChangeSet.Range[] deletions = changeSet.getDeletionRanges();
                for (int i = deletions.length - 1; i >= 0; i--) {
                    OrderedCollectionChangeSet.Range range = deletions[i];
                    notifyItemRangeRemoved(range.startIndex, range.length);
                }

                OrderedCollectionChangeSet.Range[] insertions = changeSet.getInsertionRanges();
                for (OrderedCollectionChangeSet.Range range : insertions) {
                    notifyItemRangeInserted(range.startIndex, range.length);
                }

                if (!updateOnModification) {
                    return;
                }

                OrderedCollectionChangeSet.Range[] modifications = changeSet.getChangeRanges();
                for (OrderedCollectionChangeSet.Range range : modifications) {
                    notifyItemRangeChanged(range.startIndex, range.length);
                }
            }
        };
    }

    /**
     * ctor
     *
     * @param data
     * @param autoUpdate
     */
    public RealmSectionedRecyclerViewAdapter(OrderedRealmCollection<T> data, boolean autoUpdate) {
        this(data, autoUpdate, true, -1);
    }

    /**
     * ctor
     *
     * @param data
     * @param autoUpdate
     * @param updateOnModification
     */
    public RealmSectionedRecyclerViewAdapter(OrderedRealmCollection<T> data, boolean autoUpdate, boolean updateOnModification) {
        this(data, autoUpdate, updateOnModification, -1);
    }

    /**
     * ctor
     *
     * @param data
     * @param autoUpdate
     * @param updateOnModification
     * @param itemsPerSection
     */
    public RealmSectionedRecyclerViewAdapter(OrderedRealmCollection<T> data, boolean autoUpdate, boolean updateOnModification, int itemsPerSection) {

        this.autoUpdate = autoUpdate;
        this.updateOnModification = updateOnModification;

        this.itemsPerSection = itemsPerSection;

        this.sectionData = new SparseArray<>();

        this.listener = this.autoUpdate ? createListener() : null;

        // update data
        this.update(data);
    }

    /* ---- override methods(s) ---- */

    @Override
    public void onAttachedToRecyclerView(final RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        if (autoUpdate && isDataValid()) {
            addListener(adapterData);
        }
    }

    @Override
    public void onDetachedFromRecyclerView(final RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (autoUpdate && isDataValid()) {
            //noinspection ConstantConditions
            removeListener(adapterData);
        }
    }

    @Override
    public long getItemId(int position) {
//        if (!isSection(position))
//            return ((File) getItem(position)).getId();
        return position;
    }

    @Override
    public int getItemCount() {
        return isDataValid() ? (adapterData.size() + sectionData.size()) : 0;
    }

    /* ---- abstract methods(s) ---- */

    public abstract CharSequence getSectionTitle(T data);

    /* ---- public methods(s) ---- */

    @Nullable
    public T getItem(int position) {
        return isSection(position)
                ? null
                : adapterData.get(sectionedPositionToPosition(position));
    }

    @Nullable
    public Section getSection(int position) {
        return isSection(position)
                ? sectionData.get(position)
                : null;
    }

    @Nullable
    public OrderedRealmCollection<T> getData() {
        return adapterData;
    }

    public boolean isSection(int position) {
        return sectionData.get(position) != null;
    }

    public void refresh() {
        this.update(adapterData);
    }

    public void update(@Nullable OrderedRealmCollection<T> data) {

        // data (add listener)
        if (autoUpdate) {

            if (isDataValid()) {
                removeListener(adapterData);
            }

            if (data != null) {
                addListener(data);
            }
        }

        // data
        this.adapterData = data;

        // clear section(s)
        this.sectionData.clear();

        // check
        if (this.adapterData == null) {

            this.notifyDataSetChanged();

            // return
            return;
        }

        // local var(s)

        int sectionOffset = 0;
        int firstPosition = 0;
        CharSequence lastSectionTitle = "";

        Iterator iterator = this.adapterData.iterator();

        int currentItemCount = 0;
        while (iterator.hasNext()) {

            if (itemsPerSection > 0 && currentItemCount > itemsPerSection) {
                break;
            }

            // item
            T item = (T) iterator.next();

            // header
            CharSequence sectionTitle = this.getSectionTitle(item);

            if (!TextUtils.equals(lastSectionTitle, sectionTitle)) {

                // create section
                Section section = new Section();

                section.firstPosition = firstPosition;
                section.sectionPosition = firstPosition + sectionOffset;
                section.sectionTitle = sectionTitle;

                sectionData.append(section.sectionPosition, section);

                // increment sectioned position
                // ++firstPosition;
                ++sectionOffset;

                // reset last section title
                lastSectionTitle = sectionTitle;
            }

            // increment first position
            ++firstPosition;

            // ...
            ++currentItemCount;
        }

        // notify data set changed
        this.notifyDataSetChanged();
    }

    /* ---- private method(s) ---- */

    private int sectionedPositionToPosition(int sectionedPosition) {

        if (isSection(sectionedPosition)) {
            return -1;
        }

        int offset = 0;
        for (int i = 0; i < sectionData.size(); i++) {
            if (sectionData.valueAt(i).sectionPosition > sectionedPosition) {
                break;
            }
            --offset;
        }

        return sectionedPosition + offset;
    }

    /* ---- Private Method(s) ---- */

    private void addListener(@NonNull OrderedRealmCollection<T> data) {
        if (data instanceof RealmResults) {
            RealmResults<T> results = (RealmResults<T>) data;
            //noinspection unchecked
            results.addChangeListener(listener);
        } else if (data instanceof RealmList) {
            RealmList<T> list = (RealmList<T>) data;
            //noinspection unchecked
            list.addChangeListener(listener);
        } else {
            throw new IllegalArgumentException("RealmCollection not supported: " + data.getClass());
        }
    }

    private void removeListener(@NonNull OrderedRealmCollection<T> data) {
        if (data instanceof RealmResults) {
            RealmResults<T> results = (RealmResults<T>) data;
            //noinspection unchecked
            results.removeChangeListener(listener);
        } else if (data instanceof RealmList) {
            RealmList<T> list = (RealmList<T>) data;
            //noinspection unchecked
            list.removeChangeListener(listener);
        } else {
            throw new IllegalArgumentException("RealmCollection not supported: " + data.getClass());
        }
    }

    /**
     * Check data validity
     *
     * @return
     */
    private boolean isDataValid() {
        return adapterData != null && adapterData.isValid();
    }

    /**
     * Section class
     */
    public static class Section {

        public int firstPosition;
        public int sectionPosition;
        public int sectionItems;
        public CharSequence sectionTitle;

        /**
         * ctor
         */
        public Section() {
        }

        /**
         * Get title
         *
         * @return
         */
        public CharSequence getTitle() {
            return sectionTitle;
        }
    }
}
