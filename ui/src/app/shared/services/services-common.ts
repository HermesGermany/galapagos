import { BehaviorSubject, firstValueFrom, Observable, ReplaySubject } from 'rxjs';
import { map, retry } from 'rxjs/operators';
import { HttpHeaders } from '@angular/common/http';

export class ReplayContainer<T> {

    private subject: ReplaySubject<T>;

    private loadingStatus = new BehaviorSubject<boolean>(false);

    constructor(private refresher: () => Observable<any>) {
    }

    getObservable(): Observable<T> {
        if (!this.subject) {
            this.subject = new ReplaySubject<T>(1);
            this.refresh().then();
        }
        return this.subject;
    }

    getLoadingStatus(): Observable<boolean> {
        return this.loadingStatus;
    }

    setRefresher(refresher: () => Observable<any>): void {
        this.refresher = refresher;
    }

    next(value: T) {
        if (!this.subject) {
            this.subject = new ReplaySubject<T>(1);
        }
        this.subject.next(value);
    }

    error(err: any) {
        if (!this.subject) {
            this.subject = new ReplaySubject<T>(1);
        }
        this.subject.error(err);
    }

    async refresh(): Promise<any> {
        this.loadingStatus.next(true);
        return firstValueFrom(this.refresher().pipe(retry(1)).pipe(map(data => data as T)))
            .then(value => this.next(value), error => this.error(error)).finally(() => this.loadingStatus.next(false));
    }

}

export const jsonHeader: () => HttpHeaders = () => new HttpHeaders().set('Content-Type', 'application/json');
