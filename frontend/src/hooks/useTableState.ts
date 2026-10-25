import { useState } from 'react';
import type { SortState } from '../components/ui/DataTable';

/** Page + sort state for server-paginated tables. Changing the sort resets to the first page. */
export function useTableState(defaultSort: SortState, size = 20) {
  const [page, setPage] = useState(0);
  const [sort, setSortState] = useState<SortState>(defaultSort);
  const setSort = (s: SortState) => {
    setSortState(s);
    setPage(0);
  };
  return { page, setPage, sort, setSort, size, sortParam: `${sort.key},${sort.direction}` };
}
