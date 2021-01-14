import { Observable, ReplaySubject, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';

export const SORT_ASC = 'asc';
export const SORT_DESC = 'desc';
export const SORT_NONE = '';

export type SortDirection = 'asc' | 'desc' | '';


interface SortStackEntry {
    column: string;

    direction: SortDirection;
}

/**
 * Controller for sorting arrays provided by Observables. The Controller provides a new Observable which
 * fires when either the original Observable provides a new value, or the sorting changes. The provided
 * Observable is a ReplaySubject with a buffer size of 1, so it provides the latest sorted array also to
 * subscribers registering late.
 *
 * Clients must create an object of this class first, passing their custom comparer method. Afterwards,
 * sort() can be applied on any observable to return a piped observable.
 *
 * @template T The type of the elements in the array provided by the Observable.
 */
export class SortController<T> {

    private sortStack: SortStackEntry[] = [];

    private sortStackSubject = new ReplaySubject<SortStackEntry[]>(1);

    constructor(private comparer: (a: T, b: T, column: string, direction: SortDirection) => number) {
    }

    public addSort(column: string, direction: SortDirection) {
        const index = this.sortStack.findIndex(e => e.column === column);
        if (index > -1) {
            this.sortStack.splice(index, 1);
        }
        if (direction !== '') {
            this.sortStack.push({ column: column, direction: direction });
        }
        this.sortStackSubject.next(this.sortStack);
    }

    public sort(data: Observable<T[]>): Observable<T[]> {
        this.sortStackSubject.next(this.sortStack);
        return combineLatest([data, this.sortStackSubject]).pipe(map(value => this.doSort(value[0], value[1])));
    }

    private doSort(data: T[], sortStack: SortStackEntry[]): T[] {
        // copy array to avoid potential side effects
        const sortedArray = [...data];
        sortedArray.sort((a, b) => sortStack.reduce((pv: number, cv: SortStackEntry) => {
                if (pv !== 0) {
                    return pv;
                }
                return this.comparer(a, b, cv.column, cv.direction);
            }, 0));

        return sortedArray;
    }

}
