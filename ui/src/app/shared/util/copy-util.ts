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
        document.execCommand('copy');
    }
    document.body.removeChild(selBox);
};

export const copyObs = (observer: Observable<string>) => {
    const selBox = createTextArea();
    const subscription = observer.subscribe(value => {
        selBox.value = value;
        document.body.appendChild(selBox);
        selBox.focus();
        selBox.select();
        if (navigator.clipboard) {
            navigator.clipboard.writeText(value);
        } else {
            document.execCommand('copy');
        }
        document.body.removeChild(selBox);
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
