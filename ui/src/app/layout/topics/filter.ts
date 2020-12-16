import { Observable, ReplaySubject, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';

export class FilterController<T, S> {

    private searchData = new ReplaySubject<S>(1);

    constructor(private elementFilter: (value: T, searchData: S) => boolean, initialSearchData: S) {
        this.searchData.next(initialSearchData);
    }

    setSearchData(searchData: S) {
        this.searchData.next(searchData);
    }

    filter(data: Observable<T[]>): Observable<T[]> {
        return combineLatest([data, this.searchData]).pipe(map((values: [T[], S]) => this.doFilter(values[0], values[1])));
    }

    private doFilter(data: T[], searchData: S): T[] {
        return data.filter(t => this.elementFilter(t, searchData));
    }

}

