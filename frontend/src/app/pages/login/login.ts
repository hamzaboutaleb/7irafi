import { Component } from '@angular/core';
import { LucideDynamicIcon, LucideArrowRight } from '@lucide/angular';

@Component({
  selector: 'app-login',
  imports: [LucideDynamicIcon],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  arrowIcon = LucideArrowRight;
}
