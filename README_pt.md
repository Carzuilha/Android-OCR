# Reconhecimento Ótico de Caracteres (OCR) para Android

Este projeto exibe um exemplo de aplicativo em Android para o Reconhecimento Ótico de Caracteres (OCR) em tempo real.

## Introdução

Atualmente, este projeto é baseado em dois outros projetos:

 - O projeto [Google Vision](https://developers.google.com/vision/);
 - O projeto [Camera2Vision](https://github.com/EzequielAdrianM/Camera2Vision) do EzequielAdrianM.

Os dois projetos foram combinados neste trabalho. A decisão de realizar tal implementação parte do fato que o projeto do Google utiliza a biblioteca [Camera](https://developer.android.com/reference/android/hardware/Camera.html) original (obsoleta a partir da API 21), enquanto o projeto do Ezequiel utiliza a biblioteca [Camera2](https://developer.android.com/reference/android/hardware/camera2/package-summary), mais recente, atualmente utilizada como padrão em projetos novos.

## Materiais Utilizados

 - [**Android Studio IDE**](https://developer.android.com/studio/);
 - Um _smartphone_/_tablet_ com Android (API 21 ou acima).

## Utilização dos Códigos do Projeto

Você pode utilizar os códigos deste projeto da seguinte maneira: 

 - A classe [CameraControl_A.java](app/src/main/java/com/carzuilha/ocr/control/CameraControl_A.java) utiliza a biblioteca `camera` original;
 - A classe [CameraControl_B.java](app/src/main/java/com/carzuilha/ocr/controlCameraControl_B.java) utiliza a biblioteca `camera2`.

## Informações Adicionais

O código baseado na biblioteca `camera` (_CameraControl_A.java_) apresenta um desempenho melhor do que o código baseado na biblioteca `camera2` (_CameraControl_B.java_). Futuramente este último código será otimizado, prometo. 😃 

## Licença de Uso

Os códigos disponibilizados aqui estão sob a licença Apache, versão 2.0 (veja o arquivo `LICENSE` em anexo para mais detalhes). Dúvidas sobre este projeto podem ser enviadas para o meu e-mail: carloswdecarvalho@outlook.com.