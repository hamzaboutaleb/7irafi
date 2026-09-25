import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '../../pipes/translate-pipe.js';

@Component({
  selector: 'app-hero',
  imports: [RouterLink, TranslatePipe],
  templateUrl: './hero.html',
  styleUrl: './hero.css',
})
export class Hero {

}
