package com.example.demo;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import jakarta.mail.MessageRemovedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HelloWorldWorker {
    String[] produtosDisponiveis = { "Alface", "Maçã", "Banana", "Tomate", "Cenoura" };

    @Autowired
    private JavaMailSender javaMailSender;

    Logger logger = LoggerFactory.getLogger(HelloWorldWorker.class);

    @JobWorker(type = "hello-world-worker", autoComplete = true)
    public Map<String, Object> handleHelloWorld(final JobClient client, final ActivatedJob job) {

        logger.info("Hello world, Set Data Worker executed...");

        HashMap<String, Object> var = new HashMap<String, Object>();
        var.put("orderId", "3335-2024");

        return var;
    }

    @JobWorker(type = "procura-produto-worker", autoComplete = true, fetchAllVariables = true)
    public void handleProductSearch(final JobClient client, final ActivatedJob job) {
        logger.info("Product Search Worker executed...");

        // Obtém todas as variáveis do job
        Map<String, Object> variables = job.getVariablesAsMap();

        // Obtém o nome do produto da variável
        String nomeProduto = (String) variables.get("nomeProduto");

        // Verifica se o produto está na lista
        boolean existeProd = Arrays.asList(produtosDisponiveis).contains(nomeProduto);

        if (existeProd) {
            logger.info("Produto encontrado na lista: {}", nomeProduto);
        } else {
            logger.info("Produto nao encontrado na lista: {}", nomeProduto);
        }

        // Conclui o trabalho, fornecendo as variáveis necessárias (se houver)
        // e marcando-o como concluído
        client.newCompleteCommand(job.getKey()).variables(
                Map.of("existeProd", existeProd)).send().join();
    }

    @JobWorker(type = "sendmail", fetchAllVariables = true)
    public void CheckCelebAge(final JobClient client, final ActivatedJob job) throws jakarta.mail.MessagingException {
        logger.info("Send Email Worker executed...");

        Map<String, Object> variablesAsMap = job.getVariablesAsMap();
        String sender = "urbanmarketdev@gmail.com";
        String receiver = variablesAsMap.get("email").toString();
        String subject = "Resposta à sugestão de produto";
        String resposta = variablesAsMap.get("Validacao").toString();
        String nomeProduto = variablesAsMap.get("NomeProduto").toString();

        if (resposta.equals("true")) {
            resposta = "O produto " + nomeProduto +" já está disponível na aplicação!";
            // Adicionar o produto à lista de produtos disponíveis
            // Adicionar o produto à lista de produtos disponíveis
            String[] produtosDisponiveisTemp = Arrays.copyOf(produtosDisponiveis, produtosDisponiveis.length + 1);
            produtosDisponiveisTemp[produtosDisponiveisTemp.length - 1] = nomeProduto;
            produtosDisponiveis = produtosDisponiveisTemp;
        } else if (resposta.equals("false")) {
            resposta = "O produto que sugeriu não é válido!";
        }

        List<String> invalidEmailAddresses = new ArrayList<>();
        boolean invalidEmails = false;

        if (!ValidateEmail.isValidEmail(receiver)) {
            invalidEmailAddresses.add(receiver);
            invalidEmails = true;
        }

        if (invalidEmails) {
            client.newThrowErrorCommand(job)
                    .errorCode("INVALID_EMAIL")
                    .send();
        } else {
            try {
                sendMail(sender, receiver, subject, resposta);
                String resultMessage = "Mail Sent Successfully to " + receiver;

                HashMap<String, Object> variables = new HashMap<>();
                variables.put("result", resultMessage);
                client.newCompleteCommand(job.getKey())
                        .variables(variables)
                        .send()
                        .exceptionally((throwable -> {
                            throw new RuntimeException("Could not complete job", throwable);
                        }));
            } catch (MessageRemovedException e) {
                e.printStackTrace();
                client.newFailCommand(job.getKey());
            }
        }
    }

    private void sendMail(String sender, String receiver, String subject, String body)
            throws MessageRemovedException, jakarta.mail.MessagingException {
        jakarta.mail.internet.MimeMessage message = javaMailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(sender);
        helper.setTo(receiver);
        helper.setSubject(subject);
        helper.setText(body, true);

        javaMailSender.send(message);
    }
}
