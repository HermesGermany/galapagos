import { combineLatest, concat, Observable, of } from 'rxjs';
import { map, shareReplay } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';

// function to wrap ngx-translate service EventEmitter into a useful replay observable
export const newCurrentLangObservable = (translateService: TranslateService): Observable<string> =>
    concat(of(translateService.currentLang), translateService.onLangChange.pipe(map(event => event.lang)))
        .pipe(shareReplay(1));

export const withCurrentLanguage = <T, R>(translateService: TranslateService, observable: Observable<T>,
    project: (lang: string, value: T) => R): Observable<R> =>
        combineLatest([newCurrentLangObservable(translateService), observable]).pipe(
            map(values => project(values[0], values[1]))
        );
