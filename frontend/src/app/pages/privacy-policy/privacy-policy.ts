import { Component } from '@angular/core';

import { Footer } from '../../components/footer/footer.js';
import { Navbar } from '../../components/navbar/navbar.js';

@Component({
  selector: 'app-privacy-policy',
  imports: [Navbar, Footer],
  templateUrl: './privacy-policy.html',
  styleUrl: './privacy-policy.css',
})
export class PrivacyPolicy {}
