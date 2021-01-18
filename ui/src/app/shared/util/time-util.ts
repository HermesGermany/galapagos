import * as moment from 'moment';

export const toNiceTimestamp: (utc: string) => string = utc => {
    if (!utc || !utc.length) {
        return '';
    }

    return moment.utc(utc).local().format('DD.MM.YYYY HH:mm');
};
