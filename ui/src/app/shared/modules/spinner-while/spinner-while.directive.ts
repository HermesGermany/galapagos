import { Directive, ElementRef, Renderer2 } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

interface SpinnerData {
    spinner: any;

    hiddenElements: Array<any>;
}

@Directive({
    selector: '[appSpinnerWhile]',
    exportAs: 'appSpinnerWhile'
})
export class SpinnerWhileDirective {

    constructor(private elementRef: ElementRef, private renderer: Renderer2) { }

    private buildSpinner(nativeElement: any): SpinnerData {
        const result = {
            spinner: null,
            hiddenElements: []
        };

        this.renderer.setAttribute(nativeElement, 'disabled', 'disabled');

        result.spinner = this.renderer.createElement('i');
        this.renderer.setAttribute(result.spinner, 'class', 'fas fa-cog fa-spin');

        let child = nativeElement.firstChild;
        while (child) {
            result.hiddenElements.push(child);
            const nextChild = this.renderer.nextSibling(child);
            this.renderer.removeChild(nativeElement, child);
            child = nextChild;
        }

        this.renderer.appendChild(nativeElement, result.spinner);
        return result;
    }

    while(promise: Promise<any>): Promise<any> {
        if (!promise) {
            return Promise.resolve(null);
        }
        const spinnerData = this.buildSpinner(this.elementRef.nativeElement);

        promise.finally(() => {
            this.removeSpinner(spinnerData);
        });

        return promise;
    }

    untilNext<T>(observable: Observable<T>): Observable<T> {
        if ((<any>observable).__spinnerWhileObservable) {
            return (<any>observable).__spinnerWhileObservable;
        }
        const spinnerData = [ this.buildSpinner(this.elementRef.nativeElement) ];

        const endSpin = () => {
            if (spinnerData.length) {
                this.removeSpinner(spinnerData.splice(0, 1)[0]);
            }
        };

        const result = observable.pipe(tap({
                next: () => endSpin(),
                error: () => endSpin()
            })
        );
        (<any>observable).__spinnerWhileObservable = result;
        return result;
    }

    private removeSpinner(spinnerData: SpinnerData) {
        this.renderer.removeChild(this.elementRef.nativeElement, spinnerData.spinner);
        this.renderer.removeAttribute(this.elementRef.nativeElement, 'disabled');
        spinnerData.hiddenElements.forEach(elem => this.renderer.appendChild(this.elementRef.nativeElement, elem));
    }

}
