import { Component, OnDestroy, OnInit } from '@angular/core';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { Toast, ToastService } from '../shared/modules/toast/toast.service';
import { Subscription } from 'rxjs';
import { ToastHideEvent } from '../shared/modules/toast/toast.component';
import { AuthService } from '../shared/services/auth.service';

interface DisplayToast {
    toast: Toast;

    status: string;

    key: string;
}

@Component({
    selector: 'app-layout',
    templateUrl: './layout.component.html',
    styleUrls: ['./layout.component.scss'],
    animations: [
        trigger('fadeoutToast', [
            state('visible', style({ opacity: 1 })),
            state('hidden', style({ opacity: 0, display: 'none' })),
            state('hidden-fast', style({ opacity: 0, display: 'none' })),
            transition('visible=>hidden', [ animate('800ms', style({ opacity: 0 }))]),
            transition('visible=>hidden-fast', [ animate('100ms', style({ opacity: 0 }))])
        ])
    ]
})
export class LayoutComponent implements OnInit, OnDestroy {

    collapedSideBar: boolean;

    toasts: DisplayToast[] = [];

    private toastSubscription: Subscription;

    constructor(private toastService: ToastService, public authService: AuthService) {
    }

    ngOnInit() {
        this.toastSubscription = this.toastService.getToasts().subscribe(toast => this.addToast(toast));
    }

    ngOnDestroy() {
        if (this.toastSubscription) {
            this.toastSubscription.unsubscribe();
            this.toastSubscription = null;
        }
    }

    addToast(toast: Toast) {
        const key = (Math.random() * 1000000).toString(16);
        this.toasts = [
            ...this.toasts,
            {
                toast: toast,
                status: 'visible',
                key: key
            }
        ];
        window.scrollTo(0, 0);
    }

    hideToast(toastKey: string, event: ToastHideEvent) {
        this.toasts.find(t => t.key === toastKey).status = event.closedByUser ? 'hidden-fast' : 'hidden';

        // cleanup internal array - delay must be greater than animation time from above
        setTimeout(() => {
            this.toasts = this.toasts.filter(t => t.key !== toastKey);
        }, 2000);
    }

    receiveCollapsed($event: any) {
        this.collapedSideBar = $event;
    }
}
