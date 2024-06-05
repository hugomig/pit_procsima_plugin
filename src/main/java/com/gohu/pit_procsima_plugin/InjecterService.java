package com.gohu.pit_procsima_plugin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class InjecterService extends AnAction {

    private void ajouterDependance(String serviceClass, String serviceName, AnActionEvent event) {
        Editor editor = CommonDataKeys.EDITOR.getData(event.getDataContext());
        if (editor == null) {
            show("Il est nécessaire de se positionner dans une classe pour lancer cette commande");
            return;
        }

        PsiFile psiFile = PsiDocumentManager.getInstance(event.getProject()).getPsiFile(editor.getDocument());
        if (psiFile instanceof PsiJavaFile) {
            PsiJavaFile psiJavaFile = (PsiJavaFile) psiFile;
            if (psiJavaFile.getClasses().length < 1) {
                show("Il n'y a aucune classe dans ce fichier");
                return;
            }

            PsiClass psiClass = psiJavaFile.getClasses()[0];
            PsiType serviceType = PsiType.getTypeByName(serviceClass, event.getProject(), GlobalSearchScope.allScope(event.getProject()));

            WriteCommandAction.runWriteCommandAction(event.getProject(), () -> {
                ajouterImportService(event, psiJavaFile, serviceClass);
                ajouterDeclarationAttribut(event, psiClass, serviceType, serviceName);
                ajouterParametreConstructeur(event, psiClass, serviceType, serviceName);
                ajouterImplementationConstructeur(event, psiClass, serviceName);
                ajouterCommentaireConstructeur(event, psiClass, serviceName);
            });
        } else {
            show("Vous n'êtes pas positionné dans un fichier Java");
        }
    }

    private void ajouterImportService(AnActionEvent event, PsiJavaFile psiJavaFile, String serviceClass) {
        PsiClass[] psiClasses = PsiShortNamesCache.getInstance(event.getProject())
                .getClassesByName(serviceClass, GlobalSearchScope.projectScope(event.getProject()));
        if (psiClasses.length < 1) {
            show("La classe \"" + serviceClass + "\" n'a pas été trouvée dans le projet");
        }
        PsiClass servicePsiClass = psiClasses[0];
        psiJavaFile.importClass(servicePsiClass);
    }

    private void ajouterDeclarationAttribut(AnActionEvent event, PsiClass psiClass, PsiType serviceType, String serviceName) {
        PsiField field = JavaPsiFacade.getElementFactory(event.getProject()).createField(serviceName, serviceType);
        PsiUtil.setModifierProperty(field, PsiModifier.PRIVATE, true);
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
        psiClass.add(field);
    }

    private void ajouterParametreConstructeur(AnActionEvent event, PsiClass psiClass, PsiType serviceType, String serviceName) {
        PsiMethod constructor = psiClass.getConstructors()[0];
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(event.getProject());

        PsiParameter parametre = factory.createParameter(serviceName, serviceType);
        PsiUtil.setModifierProperty(parametre, PsiModifier.FINAL, true);
        constructor.getParameterList().add(parametre);
    }

    private void ajouterImplementationConstructeur(AnActionEvent event, PsiClass psiClass, String serviceName) {
        PsiMethod constructor = psiClass.getConstructors()[0];
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(event.getProject());

        String expression = "this." + serviceName + " = " + serviceName + ";";
        PsiStatement psiStatement = factory.createStatementFromText(expression, psiClass);
        constructor.getBody().add(psiStatement);
    }

    private void ajouterCommentaireConstructeur(AnActionEvent event, PsiClass psiClass, String serviceName) {
        PsiMethod constructor = psiClass.getConstructors()[0];
        PsiDocComment docComment = constructor.getDocComment();

        PsiDocTag paramTag = JavaPsiFacade.getElementFactory(event.getProject())
                .createDocTagFromText("@param " + serviceName + " the " + serviceName);
        docComment.add(paramTag);
    }

    private void showInputServiceDialog(AnActionEvent event) {
        JPanel panel = new JPanel(new GridLayout(2, 2));
        JTextField serviceClassField = new JTextField(20);
        JTextField serviceNameField = new JTextField(20);

        serviceClassField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChangeServiceClass();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChangeServiceClass();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChangeServiceClass();
            }

            private void onChangeServiceClass() {
                String serviceClass = serviceClassField.getText();
                String serviceName = serviceClass;
                if (serviceClass.startsWith("ICustom")) {
                    serviceName = serviceName.replace("ICustom", "custom");
                } else {
                    serviceName = Character.toLowerCase(serviceName.charAt(0)) + serviceName.substring(1);
                }
                serviceNameField.setText(serviceName);
            }
        });

        panel.add(new JLabel("Nom de la classe (ex: DemandeBesoinService) :"));
        panel.add(serviceClassField);
        panel.add(new JLabel("Nom du service (ex: demandeBesoinService) :"));
        panel.add(serviceNameField);

        JFrame window = WindowManager.getInstance().getFrame(event.getProject());

        int result = JOptionPane.showConfirmDialog(window, panel, "Injecter un service", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String serviceName = serviceNameField.getText().trim();
            String serviceClass = serviceClassField.getText().trim();

            if (serviceName.isEmpty() || serviceClass.isEmpty()) {
                show("Les deux champs doivent être renseignés");
                return;
            }

            ajouterDependance(serviceClass, serviceName, event);
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        showInputServiceDialog(e);
    }

    private void show(String message) {
        Notification notification = new Notification("Print", "", message, NotificationType.ERROR);
        ApplicationManager.getApplication().getMessageBus().syncPublisher(Notifications.TOPIC).notify(notification);
    }
}
