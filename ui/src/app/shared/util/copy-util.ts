import { Observable } from 'rxjs';

export const copy = (value: string) => {
    const selBox = createTextArea();
    selBox.value = value;
    document.body.appendChild(selBox);
    selBox.focus();
    selBox.select();
    if (navigator.clipboard) {
        navigator.clipboard.writeText(value);
    } else {
        // noinspection JSDeprecatedSymbols
        document.execCommand('copy');
    }
    document.body.removeChild(selBox);
};

export const copyObsValue = (observer: Observable<string>) => {
    const subscription = observer.subscribe(value => {
        copy(value);
        subscription.unsubscribe();
    });
};


const createTextArea = (): HTMLTextAreaElement => {
    const textarea = document.createElement('textarea');
    textarea.style.position = 'fixed';
    textarea.style.left = '0';
    textarea.style.top = '0';
    textarea.style.opacity = '0';
    return textarea;
};
