import { Component } from '@angular/core';
import { Navbar } from "../../components/navbar/navbar";
import { Hero } from "../../components/hero/hero";
import { Categories } from "../../components/categories/categories";
import { HowItWorks } from "../../components/how-it-works/how-it-works";

@Component({
  selector: 'app-landing',
  imports: [Navbar, Hero, Categories, HowItWorks],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class Landing {}
