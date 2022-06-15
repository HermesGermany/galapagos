import { DateTime } from 'luxon';
import { Observable, of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { newCurrentLangObservable } from './translate-util';
import { map } from 'rxjs/operators';

const formatAsNiceTimestamp = (datetime: DateTime) => datetime.toLocaleString({
    month: '2-digit',
    day: '2-digit',
    year: 'numeric'
}) + ' ' + datetime.toLocaleString(DateTime.TIME_SIMPLE);

export const toNiceTimestamp = (utc: string, translateService: TranslateService): Observable<string> => {
    if (!utc || !utc.length) {
        return of('');
    }

    return newCurrentLangObservable(translateService).pipe(map(lang => formatAsNiceTimestamp(DateTime.fromISO(utc).setLocale(lang))));
};
