import React from "react";
import { MainLayout } from "@/components/layout/MainLayout";
import { AdminLessonWizard } from "@/features/admin/wizard/AdminLessonWizard";

const CreateLessonPage = () => (
  <MainLayout>
    <div className="container mx-auto max-w-5xl px-4 py-6 md:py-8 pb-safe">
      <AdminLessonWizard mode="create" />
    </div>
  </MainLayout>
);

export default CreateLessonPage;
