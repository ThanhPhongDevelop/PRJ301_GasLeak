package model;

import java.util.Collections;
import java.util.List;

/** One page of a list plus what the pager needs. */
public class PageResult<T> {

    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final int totalRows;

    public PageResult(List<T> items, int page, int pageSize, int totalRows) {
        this.items = items == null ? Collections.<T>emptyList() : items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalRows = totalRows;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getTotalPages() {
        return Math.max(1, (totalRows + pageSize - 1) / pageSize);
    }

    public boolean isFirstPage() {
        return page <= 1;
    }

    public boolean isLastPage() {
        return page >= getTotalPages();
    }
}
