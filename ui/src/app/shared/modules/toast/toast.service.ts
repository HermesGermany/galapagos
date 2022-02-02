import { Injectable } from '@angular/core';
import { Observable, ReplaySubject } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { TopicsService } from '../../services/topics.service';

export type ToastType = 'info' | 'success' | 'warning' | 'danger';

export interface Toast {

    type: ToastType;

    delay: number;

    header: string;

    message: string;

}

const ERROR_DELAY = 20000;

@Injectable()
export class ToastService {

    private toasts = new ReplaySubject<Toast>();

    constructor(
        private translate: TranslateService
    ) {}

    public getToasts(): Observable<Toast> {
        return this.toasts;
    }

    public addToast(type: ToastType, header: string, message: string, delay: number = 5000) {
        this.toasts.next({
            type: type,
            header: header,
            message: message,
            delay: delay
        });
    }

    public addHttpErrorToast(messageKey: string, err: HttpErrorResponse) {
        let message = this.translate.instant(messageKey);

        if (err.error && err.error.message) {
            message += ': ' + err.error.message;
        } else if (err.message) {
            message += ': ' + err.message;
        }
        this.addToast('danger', this.translate.instant('ERROR'), message, ERROR_DELAY);
    }

    public addSuccessToast(messageKey: string) {
        this.addToast('success', this.translate.instant('SUCCESS'), this.translate.instant(messageKey));
    }

    public addWarningToast(messageKey: string) {
        this.addToast('warning', this.translate.instant('WARNING'), this.translate.instant(messageKey));
    }

    public addErrorToast(messageKey: string) {
        this.addToast('danger', this.translate.instant('ERROR'), this.translate.instant(messageKey), ERROR_DELAY);
    }
}
