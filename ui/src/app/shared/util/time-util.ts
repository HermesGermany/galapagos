import { DateTime } from 'luxon';

export const toNiceTimestamp: (utc: string) => string = utc => {
    if (!utc || !utc.length) {
        return '';
    }

    return DateTime.utc(utc).local().format('DD.MM.YYYY HH:mm');
};
