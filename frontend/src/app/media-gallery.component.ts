import { Component, input } from '@angular/core';
import { Media } from './models';

@Component({
  selector: 'app-media-gallery', standalone: true,
  template: `<div class="media-grid">@for (item of items(); track item.id) {
    @if (item.type.startsWith('video/')) { <video controls playsinline preload="metadata" [src]="'/api/media/' + item.id" [attr.aria-label]="label() + ' video'"></video> }
    @else { <a [href]="'/api/media/' + item.id" target="_blank" rel="noopener" [attr.aria-label]="'Open ' + label() + ' photo in a new tab'"><img [src]="'/api/media/' + item.id" [alt]="label() + ' photo'" loading="lazy"></a> }
  }</div>`
})
export class MediaGalleryComponent { items = input.required<Media[]>(); label = input('Place'); }
