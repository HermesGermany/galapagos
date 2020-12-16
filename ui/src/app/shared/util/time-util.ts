import * as moment from 'moment';

export function toNiceTimestamp(utc: string): string {
    if (!utc || !utc.length) {
        return '';
    }

    return moment.utc(utc).local().format('DD.MM.YYYY HH:mm');
}
