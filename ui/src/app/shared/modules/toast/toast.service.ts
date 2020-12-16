import { Injectable } from '@angular/core';
import { Observable, ReplaySubject } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

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

    public addHttpErrorToast(message: string, err: HttpErrorResponse) {
        if (err.error && err.error.message) {
            message += ': ' + err.error.message;
        } else if (err.message) {
            message += ': ' + err.message;
        }
        this.addToast('danger', 'FEHLER', message, ERROR_DELAY);
    }

    public addSuccessToast(message: string) {
        this.addToast('success', 'ERFOLG', message);
    }

    public addWarningToast(message: string) {
        this.addToast('warning', 'WARNUNG', message);
    }

    public addErrorToast(message: string) {
        this.addToast('danger', 'FEHLER', message, ERROR_DELAY);
    }
}
