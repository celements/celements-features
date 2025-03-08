class SendMailButton {
  #htmlElemA;
  #iconElem;
  constructor(htmlElemA) {
    this.#htmlElemA = htmlElemA;
    this.#iconElem = this.#htmlElemA.querySelector(".icon");
  }

  register() {
    console.log('registering event listener for ' + this.#htmlElemA);
    this.#htmlElemA.addEventListener('click', (ev) => this.#sendActivationMailHandler(ev));
  }

  #sendActivationMailHandler(event) {
    console.log('stopDefaultAction');
    event.preventDefault();
    this.#iconElem.classList.add("fa-spinner", "fa-spin");
    this.#sendActivationMail(this.#htmlElemA.href);
  }

  async #sendActivationMail(url) {
    console.log('sendActivationMail with url: ' + url);
    try {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error('response status: ' + response.status + ' ' + response.statusText + ' for url: ' + url);
      }
      const data = await response.json();
      console.log('successful: ' + data.successful + ' message: ' + data.message);
      this.#showOutput(data.message);
    } catch (error) { 
      console.error('Error during fetch operation:', error);
      this.#showOutput("Es ist ein Fehler aufgetreten. Bitte versuchen Sie es später erneut.\n" + error.message);
    } finally {
      this.#iconElem.classList.remove("fa-spinner", "fa-spin");
    }
  }

  #showOutput(message) {
    alert(message);
  }
}

document.querySelectorAll("a.sendMailAction").forEach((a) => new SendMailButton(a).register());
