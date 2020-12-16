/*
Based on https://github.com/ng-bootstrap/ng-bootstrap/blob/ffcdad48aaafbcf97b15ae15b01c36436ab485ca/src/toast/toast.ts
*/

import {
    AfterContentInit,
    Attribute,
    Component,
    ContentChild,
    Directive,
    EventEmitter,
    Input,
    OnChanges,
    Output,
    SimpleChanges,
    TemplateRef,
    ViewEncapsulation,
    HostBinding,
    ViewChild,
    ElementRef,
} from '@angular/core';

const TYPE_HEADER_CLASS_MAP = {
    'info': 'bg-info text-white',
    'success': 'bg-success text-white',
    'danger': 'bg-danger text-white',
    'warning': 'bg-warning text-dark'
};

export interface ToastHideEvent {
    closedByUser: boolean;
}

/**
 * This directive allows the usage of HTML markup or other directives
 * inside of the toast's header.
 */
@Directive({ selector: '[appToastHeader]' })
export class GalapagosToastHeaderDirective {}

@Component({
    selector: 'app-toast',
    encapsulation: ViewEncapsulation.None,
    template: `
        <ng-template #headerTpl>
            <strong class="mr-auto">{{ header }}</strong>
        </ng-template>
        <ng-template [ngIf]="contentHeaderTpl || header">
            <div [class]="'toast-header ' + headerClass()" style="border-bottom: none">
                <ng-template [ngTemplateOutlet]="contentHeaderTpl || headerTpl"></ng-template>
                <button type="button" class="close" aria-label="close" (click)="hide(true)">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
        </ng-template>
        <div [class]="'toast-body ' + bodyClass()">
            <ng-content></ng-content>
        </div>
    `,
    styleUrls: ['./toast.scss']
})
export class GalapagosToastComponent implements AfterContentInit, OnChanges {
    private _timeoutID;

    /**
     * Delay after which the toast will hide (ms).
     * default: `500` (ms)
     */
    @Input() delay: number;

    /**
     * Auto hide the toast after a delay in ms.
     * default: `true`
     */
    @Input()
    @HostBinding('class.autohide')
    autohide: boolean;

    @Input()
    type: 'info' | 'success' | 'danger' = 'info';

    /**
     * Text to be used as toast's header.
     * Ignored if a ContentChild template is specified at the same time.
     */
    @Input() header: string;

    /**
     * A template like `<ng-template ngbToastHeader></ng-template>` can be
     * used in the projected content to allow markup usage.
     */
    @ContentChild(GalapagosToastHeaderDirective, { read: TemplateRef, static: true }) contentHeaderTpl: TemplateRef<any> | null = null;

    @ViewChild('headerDiv', { static: true }) headerDiv: ElementRef<any>;

    /**
     * An event fired immediately when toast's `hide()` method has been called.
     * It can only occur in 2 different scenarios:
     * - `autohide` timeout fires
     * - user clicks on a closing cross (&times)
     *
     * Additionally this output is purely informative. The toast won't disappear. It's up to the user to take care of
     * that.
     */
    // tslint:disable-next-line: no-output-rename
    @Output('hide') hideOutput = new EventEmitter<ToastHideEvent>();

    @HostBinding('attr.role') role = 'alert';

    @HostBinding('attr.aria-live') ariaLive: string;

    @HostBinding('attr.aria-atomic') ariaAtomic = true;

    @HostBinding('class.toast') classToast = true;

    @HostBinding('class.show') classShow = true;

    constructor(@Attribute('aria-live') ariaLive: string) {
        this.ariaLive = ariaLive;
        if (this.ariaLive == null) {
            this.ariaLive = 'polite';
        }
        this.delay = 500;
        this.autohide = true;
    }

    headerClass() {
        return TYPE_HEADER_CLASS_MAP[this.type];
    }

    bodyClass() {
        return 'alert-' + this.type;
    }

    ngAfterContentInit() {
        this._init();
    }

    ngOnChanges(changes: SimpleChanges) {
        if ('autohide' in changes) {
            this._clearTimeout();
            this._init();
        }
    }

    hide(closedByUser: boolean) {
        this._clearTimeout();
        this.hideOutput.emit({ closedByUser: closedByUser });
    }

    private _init() {
        if (this.autohide && !this._timeoutID) {
            this._timeoutID = setTimeout(() => this.hide(false), this.delay);
        }
    }

    private _clearTimeout() {
        if (this._timeoutID) {
            clearTimeout(this._timeoutID);
            this._timeoutID = null;
        }
    }
}
