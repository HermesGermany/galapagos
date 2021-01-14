import { animate, state, style, transition, trigger } from '@angular/animations';

export const noTransition = () => trigger('routerTransition', []);

export const slideToRight = () => trigger('routerTransition', [
    state('void', style({})),
    state('*', style({})),
    transition(':enter', [
        style({ transform: 'translateX(-100%)' }),
        animate('0.5s ease-in-out', style({ transform: 'translateX(0%)' }))
    ]),
    transition(':leave', [
        style({ transform: 'translateX(0%)' }),
        animate('0.5s ease-in-out', style({ transform: 'translateX(100%)' }))
    ])
]);

export const slideToLeft = () => trigger('routerTransition', [
    state('void', style({})),
    state('*', style({})),
    transition(':enter', [
        style({ transform: 'translateX(100%)' }),
        animate('0.5s ease-in-out', style({ transform: 'translateX(0%)' }))
    ]),
    transition(':leave', [
        style({ transform: 'translateX(0%)' }),
        animate('0.5s ease-in-out', style({ transform: 'translateX(-100%)' }))
    ])
]);

export const slideToBottom = () => trigger('routerTransition', [
    state('void', style({})),
    state('*', style({})),
    transition(':enter', [
        style({ transform: 'translateY(-100%)' }),
        animate('0.5s ease-in-out', style({ transform: 'translateY(0%)' }))
    ]),
    transition(':leave', [
        style({ transform: 'translateY(0%)' }),
        animate('0.5s ease-in-out', style({ transform: 'translateY(100%)' }))
    ])
]);

export const slideToTop = () => trigger('routerTransition', [
    state('void', style({})),
    state('*', style({})),
    transition(':enter', [
        style({ transform: 'translateY(100%)' }),
        animate('0.5s ease-in-out', style({ transform: 'translateY(0%)' }))
    ]),
    transition(':leave', [
        style({ transform: 'translateY(0%)' }),
        animate('0.5s ease-in-out', style({ transform: 'translateY(-100%)' }))
    ])
]);

export const routerTransition = slideToRight;
